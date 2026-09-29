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
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.PresentKind

/**
 * What is shown over the screen right now: one presented tree and one question, at most (SPEC.md §12.5).
 *
 * The state is readable by any host, not only [KompotOverlayHost]: a design system that draws its sheet
 * its own way reads [presented] and [asking] and answers through [dismiss], [refuse] and [agree]. It is
 * written only by [withOverlays] and by those three, so a host cannot show what the server did not send,
 * and every host means the same thing by "closed" and "agreed".
 */
@Stable
public class KompotOverlays {
    /** The tree shown over the screen, or null. A `present` replaces it: there is one layer. */
    public var presented: PresentAction? by mutableStateOf(null)
        internal set

    /** The question waiting for an answer, or null. It is drawn above [presented]. */
    public var asking: ConfirmAction? by mutableStateOf(null)
        internal set

    /** Whether anything is shown over the screen. */
    public val isOpen: Boolean get() = presented != null || asking != null

    /**
     * Closes the top layer — the question, then the tree — which is what `close` does, and what a swipe
     * or a tap outside a sheet means. Returns whether anything was open to close.
     */
    public fun dismiss(): Boolean =
        when {
            asking != null -> {
                asking = null
                true
            }
            presented != null -> {
                presented = null
                true
            }
            else -> false
        }

    /** Closes the question without running its action: the person said no. */
    public fun refuse() {
        asking = null
    }

    /**
     * Closes the question and runs its action through [actionHandler], which must be the TOP of the chain
     * (see [KompotOverlayHost]): a `perform` behind a confirmation is sent like any other.
     */
    public fun agree(actionHandler: KompotActionHandler) {
        val question = asking ?: return
        asking = null
        actionHandler.handle(question.action)
    }

    // A navigation leaves the screen the layer lies over, so the layer goes with it (SPEC.md §12.5):
    // otherwise a sheet held above the screen — one KompotOverlays per navigation host — stays over the
    // next screen.
    internal fun closeAll() {
        asking = null
        presented = null
    }
}

/**
 * Records `present` and `confirm` in [overlays] for a [KompotOverlayHost] to draw, makes `close`
 * close what is shown, and makes `navigate` close everything before it goes on.
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
            is CloseAction -> if (!overlays.dismiss()) handle(action)
            is NavigateAction -> {
                overlays.closeAll()
                handle(action)
            }
            else -> handle(action)
        }
    }

/**
 * Draws what [overlays] holds the Material way: a question as an `AlertDialog`, a presented tree as a
 * `Dialog` or a `ModalBottomSheet`. A design system that draws them its own way writes its own host
 * against the public state of [KompotOverlays]; this one uses nothing else.
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
            onDismissRequest = { overlays.refuse() },
            title = { Text(question.question) },
            text = question.detail?.let { { Text(it) } },
            confirmButton = {
                TextButton(onClick = { overlays.agree(actionHandler) }) { Text(question.confirmLabel ?: confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { overlays.refuse() }) { Text(question.cancelLabel ?: cancelLabel) }
            },
        )
    }

    overlays.presented?.let { presented ->
        val dismiss: () -> Unit = { overlays.dismiss() }
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
