package io.github.youndie.kompot.ds.material

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ConfirmAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.PresentKind

/** What is shown over the screen right now: one presented tree and one question, at most (SPEC.md §12.5). */
@Stable
public class KompotOverlays {
    internal var presented: PresentAction? by mutableStateOf(null)
    internal var asking: ConfirmAction? by mutableStateOf(null)

    /** Whether anything is shown over the screen. */
    public val isOpen: Boolean get() = presented != null || asking != null
}

/**
 * Records `present` and `confirm` in [overlays] for a [KompotOverlayHost] to draw, and makes `close`
 * close what is shown.
 *
 * `close` is consumed when it closed something and forwarded otherwise. Forwarded, it would ALSO reach
 * the application, whose `close` is typically "go back" — and the one tap would close the sheet and
 * leave the screen under it. `present` and `confirm` are forwarded, as `withPerform` forwards, for an
 * analytics wrapper further along the chain.
 */
public fun KompotActionHandler.withOverlays(overlays: KompotOverlays): KompotActionHandler =
    KompotActionHandler { action ->
        when (action) {
            is ConfirmAction -> {
                overlays.asking = action
                handle(action)
            }
            is PresentAction -> {
                // One layer: a tree presented over a presented tree replaces it.
                overlays.presented = action
                handle(action)
            }
            is CloseAction ->
                when {
                    overlays.asking != null -> overlays.asking = null
                    overlays.presented != null -> overlays.presented = null
                    else -> handle(action)
                }
            else -> handle(action)
        }
    }

/**
 * Draws what [overlays] holds the Material way: a question as an `AlertDialog`, a presented tree as a
 * `Dialog` or a `ModalBottomSheet`.
 *
 * [actionHandler] must be the TOP of the chain — the one the screen itself raises actions through. The
 * agreed action and every button inside a presented tree go there, so a `perform` behind a confirmation
 * is sent like any other (the lesson of `withSnackbarMessages`' followUp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun KompotOverlayHost(
    overlays: KompotOverlays,
    actionHandler: KompotActionHandler,
    confirmLabel: String = "OK",
    cancelLabel: String = "Cancel",
    formController: FormController = remember { FormController(FormSchema(formId = "overlay", fields = emptyList())) },
) {
    val registry = LocalKompotRegistry.current

    overlays.asking?.let { question ->
        AlertDialog(
            onDismissRequest = { overlays.asking = null },
            title = { Text(question.question) },
            text = question.detail?.let { { Text(it) } },
            confirmButton = {
                TextButton(onClick = {
                    overlays.asking = null
                    actionHandler.handle(question.action)
                }) { Text(question.confirmLabel ?: confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { overlays.asking = null }) { Text(question.cancelLabel ?: cancelLabel) }
            },
        )
    }

    overlays.presented?.let { presented ->
        val dismiss = { overlays.presented = null }
        when (presented.kind) {
            PresentKind.SHEET ->
                ModalBottomSheet(onDismissRequest = dismiss) {
                    registry.RenderNode(presented.content, actionHandler, formController)
                }
            else ->
                Dialog(onDismissRequest = dismiss) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                        androidx.compose.foundation.layout.Box(Modifier.padding(24.dp)) {
                            registry.RenderNode(presented.content, actionHandler, formController)
                        }
                    }
                }
        }
    }
}
