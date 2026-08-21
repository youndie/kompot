package io.github.youndie.kompot.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
     * Subscribes to a field's changes and picks up its initial state automatically.
 */
@Composable
inline fun <reified T : FieldValue> FormController.collectFieldState(fieldId: String): State<FieldState<T>> =
    remember(fieldId) {
        this.getFieldFlow<T>(fieldId)
    }.collectAsStateWithLifecycle(
        this.getTypedState<T>(fieldId),
    )

/**
     * Subscribes to a field's visibility and picks up its initial state automatically.
 */
@Composable
fun FormController.collectVisibility(fieldId: String): State<Boolean> =
    remember(fieldId) {
        this.getVisibilityFlow(fieldId)
    }.collectAsStateWithLifecycle(
        this.isFieldVisible(fieldId),
    )
