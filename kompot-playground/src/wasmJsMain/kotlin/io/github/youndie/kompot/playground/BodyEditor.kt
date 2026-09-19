package io.github.youndie.kompot.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The left pane: the wire body, as text.
//
// TEXT, and not a tree of widgets or a DSL — the same decision the studio took (research §5.3). An
// object and the bytes on the wire can disagree, and the page's whole subject is what a client
// receives, so the thing being edited has to be what travels.
@Composable
internal fun BodyEditor(
    text: String,
    failure: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "The body an endpoint answers",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
        )

        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            isError = failure != null,
        )

        // The message stands next to the editor rather than over the screen, and the screen keeps
        // showing the last body that parsed: "what you typed is not JSON yet" and "the toolkit cannot
        // draw this" are different sentences, and only one of them is true here.
        if (failure != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    failure,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                )
            }
        }
    }
}
