package io.github.youndie.kompot.studio.tree

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

// WHAT IS BEING DRAGGED, as one string, because a drag on the desktop crosses AWT and AWT carries
// text. Two shapes and no more: a type from the palette, and a node already in the body.
internal object Dragged {
    const val NEW = "kompot/new:"
    const val MOVE = "kompot/move:"

    fun type(payload: String): String? = payload.removePrefixOrNull(NEW)

    fun path(payload: String): String? = payload.removePrefixOrNull(MOVE)

    private fun String.removePrefixOrNull(prefix: String): String? =
        if (startsWith(prefix)) removePrefix(prefix) else null
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun Modifier.dragPayload(payload: String): Modifier =
    dragAndDropSource {
        DragAndDropTransferData(
            transferable = DragAndDropTransferable(StringSelection(payload)),
            supportedActions = listOf(DragAndDropTransferAction.Copy),
        )
    }

// THE DRAG IN FLIGHT, if there is one: what is being carried, readable by anything that wants to
// draw differently while it is. Set by the targets, because on this platform a source is told nothing
// once the gesture leaves it and a target is told everything — every target hears the session start
// and end. One object rather than a state per tree: there is one mouse.
internal object DragSession {
    var payload: String? by mutableStateOf(null)
        private set

    internal fun started(payload: String?) {
        this.payload = payload
    }

    internal fun ended() {
        payload = null
    }
}

// Keyed by the ROW, not by the callback. The callback is a fresh lambda on every recomposition, so
// remembering by it would rebuild every target on every keystroke; keeping it in an updated state is
// what lets the target survive while still calling into the current body rather than a captured one.
@Composable
internal fun rememberDropTarget(
    key: Any?,
    // Whether the drag is over this row right now — what the row draws its "it would land here"
    // state from. Told on every edge, including the drop and the end of the session, so a row is
    // never left highlighted by a drag that ended somewhere else.
    onHover: (Boolean) -> Unit = {},
    onDrop: (String) -> Unit,
): DragAndDropTarget {
    val current by rememberUpdatedState(onDrop)
    val hover by rememberUpdatedState(onHover)
    return remember(key) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                DragSession.started(event.payload())
            }

            override fun onEnded(event: DragAndDropEvent) {
                DragSession.ended()
                hover(false)
            }

            override fun onEntered(event: DragAndDropEvent) {
                hover(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                hover(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover(false)
                val payload = event.payload() ?: return false
                // Refused rather than swallowed when it is not ours: a drop from a file manager or a
                // browser has to keep travelling to whatever else on screen wants it.
                if (Dragged.type(payload) == null && Dragged.path(payload) == null) return false
                current(payload)
                return true
            }
        }
    }
}

internal fun Modifier.dropZone(target: DragAndDropTarget): Modifier =
    dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.payload() != null },
        target = target,
    )

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.payload(): String? =
    runCatching {
        awtTransferable
            .takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
            ?.getTransferData(DataFlavor.stringFlavor) as? String
    }.getOrNull()
