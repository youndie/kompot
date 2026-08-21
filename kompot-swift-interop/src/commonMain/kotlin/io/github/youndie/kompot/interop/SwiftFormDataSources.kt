package io.github.youndie.kompot.interop

import kotlinx.coroutines.suspendCancellableCoroutine
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormPatch
import io.github.youndie.kompot.form.PatchFetcher
import io.github.youndie.kompot.form.RemoteDataSourceResolver

// RemoteDataSourceResolver.search and PatchFetcher are suspend contracts, and Swift cannot implement
// a Kotlin suspend interface or a suspend lambda through the ObjC export — unlike ordinary,
// non-suspend function types, which bridge to native Swift closures without trouble (see
// CoroutineScopeFactory for the same boundary from the other side).
//
// These two factories hide suspendCancellableCoroutine entirely inside Kotlin: Swift passes an
// ordinary callback of the shape "start the work, call onResult/onError when it is done", which is
// what a Swift caller already wraps in a Task. onError takes a String rather than a Throwable or an
// NSError, which keeps the untranslatable Swift Error <-> Kotlin Throwable boundary out of it; every
// real Kotlin exception is created here.
//
// One detail is load-bearing: the continuation is ALWAYS resumed with Result.success(...), even when
// Swift reported a failure through onError. The failure is wrapped in a Kotlin Result<T> and
// unwrapped (getOrThrow/getOrElse) only AFTER leaving suspendCancellableCoroutine, back in plain
// Kotlin. Resuming with Result.failure(...) from a callback invoked by a Swift Task — a different
// thread and executor, not necessarily the continuation's — produced a fatal "Uncaught Kotlin
// exception" at runtime: an internal inconsistency of CancellableContinuation on a cross-thread
// resume, and specifically on the failure branch. The empirical result is unambiguous, so the
// always-resume-with-success shape stays.

fun swiftRemoteDataSourceResolver(
    search: (
        dataSourceId: String,
        query: String,
        onResult: (List<FieldValue>) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
): RemoteDataSourceResolver =
    object : RemoteDataSourceResolver {
        override suspend fun search(
            dataSourceId: String,
            query: String,
        ): List<FieldValue> {
            val outcome =
                suspendCancellableCoroutine<Result<List<FieldValue>>> { continuation ->
                    search(
                        dataSourceId,
                        query,
                        { results -> continuation.resumeWith(Result.success(Result.success(results))) },
                        { message -> continuation.resumeWith(Result.success(Result.failure(RuntimeException(message)))) },
                    )
                }
            // A failed search must not take the screen down — the same quiet fallback as when no
            // resolver is configured at all.
            return outcome.getOrElse { emptyList() }
        }
    }

fun swiftPatchFetcher(
    fetch: (
        fieldId: String,
        payload: Map<String, FieldValue>,
        onResult: (FormPatch) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
): PatchFetcher =
    { fieldId, payload ->
        val outcome =
            suspendCancellableCoroutine<Result<FormPatch>> { continuation ->
                fetch(
                    fieldId,
                    payload,
                    { patch -> continuation.resumeWith(Result.success(Result.success(patch))) },
                    { message -> continuation.resumeWith(Result.success(Result.failure(RuntimeException(message)))) },
                )
            }
        // Unlike a search, this failure is deliberately rethrown: the caller already catches it and
        // turns it into a field error.
        outcome.getOrThrow()
    }
