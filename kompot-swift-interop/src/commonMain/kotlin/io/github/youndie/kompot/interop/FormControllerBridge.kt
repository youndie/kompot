package io.github.youndie.kompot.interop

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import io.github.youndie.kompot.form.FieldState
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController

// getTypedState<T>() and getFieldFlow<T>() on FormController are the only methods of the controller
// that do not survive the bridge: both are inline fun <reified T>, and the Kotlin/Native ObjC export
// drops inline reified functions from the generated Swift API entirely. It is a limitation of the
// export itself, independent of how Flow and suspend are bridged.
//
// These two are the same per-field access as plain, non-inline, non-reified functions, so they are
// exported as they are. Casting the resulting FieldValue to a concrete subtype is Swift's business —
// which concrete subtypes exist is not something this module knows.
fun FormController.fieldFlow(fieldId: String): Flow<FieldState<FieldValue>> =
    fieldsState.map { it[fieldId] ?: FieldState(null) }.distinctUntilChanged()

fun FormController.fieldState(fieldId: String): FieldState<FieldValue> = fieldsState.value[fieldId] ?: FieldState(null)
