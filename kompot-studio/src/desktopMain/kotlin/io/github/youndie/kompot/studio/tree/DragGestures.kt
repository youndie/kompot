package io.github.youndie.kompot.studio.tree

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

// Keyed by the ROW, not by the callback. The callback is a fresh lambda on every recomposition, so
// remembering by it would rebuild every target on every keystroke; keeping it in an updated state is
// what lets the target survive while still calling into the current body rather than a captured one.
@Composable
internal fun rememberDropTarget(
    key: Any?,
    onDrop: (String) -> Unit,
): DragAndDropTarget {
    val current by rememberUpdatedState(onDrop)
    return remember(key) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
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
