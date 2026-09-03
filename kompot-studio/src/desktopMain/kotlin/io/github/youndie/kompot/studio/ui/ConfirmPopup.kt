package io.github.youndie.kompot.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

// THE QUESTION BEFORE AN EDIT SOMEBODY CANNOT SEE COMING — a slot that overwrites, a golden drawn
// with a stub. A card where the gesture ended rather than a dialog in the middle of the screen: the
// answer is one key, and the eye should not have to travel to give it. Enter is the confirmation and
// Esc the retreat, the way every such card on this platform works; the buttons are for the mouse.
@Composable
internal fun ConfirmPopup(
    title: AnnotatedString,
    body: AnnotatedString,
    confirm: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    alignment: Alignment = Alignment.TopStart,
) {
    val colors = studioColors()
    val focus = remember { FocusRequester() }
    Popup(alignment = alignment, onDismissRequest = onCancel, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .width(CARD_WIDTH)
                .shadow(12.dp, RoundedCornerShape(8.dp))
                .background(colors.popup, RoundedCornerShape(8.dp))
                .border(1.dp, colors.controlLine, RoundedCornerShape(8.dp))
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 10.dp)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onConfirm()
                            true
                        }

                        Key.Escape -> {
                            onCancel()
                            true
                        }

                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(StudioIcon.WARNING, colors.warning, Modifier.padding(top = 1.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontWeight = FontWeight.Medium)
                    Text(body, color = colors.dim)
                }
            }
            Row(
                Modifier.padding(start = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enter · Esc", color = colors.dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                DefaultButton(onClick = onConfirm) { Text(confirm) }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

private val CARD_WIDTH = 336.dp
