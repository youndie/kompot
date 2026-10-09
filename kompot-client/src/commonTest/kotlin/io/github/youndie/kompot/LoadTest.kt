package io.github.youndie.kompot

import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.commands.kompotCommandsSerializersModule
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoadTest {
    @Test
    fun `a load sends a GET of its url and feeds the answer down the chain`() =
        runTest {
            val reached = mutableListOf<KompotAction>()
            val asked = mutableListOf<String>()
            val handler =
                KompotActionHandler { reached += it }.withLoad(this) { url ->
                    asked += url
                    NavigateAction(deeplink = "app://catalog")
                }

            handler.handle(LoadAction("/results?brand=acme"))
            // The press itself is forwarded at once, for an analytics wrapper further along.
            assertEquals(listOf<KompotAction>(LoadAction("/results?brand=acme")), reached)
            advanceUntilIdle()

            assertEquals(listOf("/results?brand=acme"), asked)
            assertEquals(NavigateAction(deeplink = "app://catalog"), reached.last())
        }

    // Cancellation is cooperative, and a transport that does not check it still returns: the press
    // number decides, not whether the earlier coroutine noticed.
    @Test
    fun `an answer to an earlier press is dropped even when its load ignores cancellation`() =
        runTest {
            val reached = mutableListOf<KompotAction>()
            val first = CompletableDeferred<KompotAction>()
            val state = KompotLoadState()
            val handler =
                KompotActionHandler { reached += it }.withLoad(this, state) { url ->
                    if (url ==
                        "/first"
                    ) {
                        withContext(NonCancellable) { first.await() }
                    } else {
                        NavigateAction("app://second")
                    }
                }

            handler.handle(LoadAction("/first"))
            advanceUntilIdle()
            handler.handle(LoadAction("/second"))
            advanceUntilIdle()
            first.complete(NavigateAction("app://first"))
            advanceUntilIdle()

            assertEquals(listOf("app://second"), reached.filterIsInstance<NavigateAction>().map { it.deeplink })
            assertFalse(state.isLoading)
        }

    @Test
    fun `a failed load does not leave the screen loading`() =
        runTest {
            val state = KompotLoadState()
            val failures = mutableListOf<Throwable>()
            // A scope of its own, under a supervisor: the failure is the application's to see, and in the
            // test's own scope it would fail the test before anything could be asserted.
            val scope =
                CoroutineScope(
                    SupervisorJob() + StandardTestDispatcher(testScheduler) +
                        CoroutineExceptionHandler { _, failure -> failures += failure },
                )
            val handler = KompotActionHandler {}.withLoad(scope, state) { error("no network") }

            handler.handle(LoadAction("/results"))
            assertTrue(state.isLoading)
            advanceUntilIdle()

            assertFalse(state.isLoading)
            assertEquals(1, failures.size)
        }

    // An answer this client cannot read is reported where it enters the chain, as withPerform reports it.
    @Test
    fun `an unknown answer is reported to the sink`() =
        runTest {
            val reported = mutableListOf<String>()
            val sink = KompotDegradationSink { _, type, _ -> reported += type }
            val handler = KompotActionHandler {}.withLoad(this, degradationSink = sink) { UnknownAction("teleport") }

            handler.handle(LoadAction("/results"))
            advanceUntilIdle()

            assertEquals(listOf("teleport"), reported)
        }

    // The engine's Json speaks the three actions the engine runs: without them an answer decoded to
    // UnknownAction and withUpdates never saw it. And an application that already adds the module
    // itself, as it had to, still gets a Json.
    @Test
    fun `the engine Json decodes perform load and update`() {
        val json = kompotJson()

        fun decode(text: String) = json.decodeFromString(PolymorphicSerializer(KompotAction::class), text)

        assertTrue(decode("""{"type":"perform","url":"/cart/add"}""") is PerformAction)
        assertTrue(decode("""{"type":"load","url":"/results"}""") is LoadAction)
        assertTrue(decode("""{"type":"update","updates":[]}""") is UpdateAction)
        assertTrue(
            kompotJson(kompotCommandsSerializersModule)
                .decodeFromString(
                    PolymorphicSerializer(KompotAction::class),
                    """{"type":"load","url":"/r"}""",
                ) is LoadAction,
        )
    }
}
