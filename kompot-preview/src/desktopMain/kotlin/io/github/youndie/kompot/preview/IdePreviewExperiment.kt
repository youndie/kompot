package io.github.youndie.kompot.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import kotlinx.serialization.PolymorphicSerializer
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import androidx.compose.ui.tooling.preview.Preview

// THE EXPERIMENT OF #109, and nothing else yet.
//
// The question is one thing only: does IntelliJ render a @Preview whose content is a kompot tree
// assembled by a function call, rather than a static composable? Everything else about the idea —
// where such previews should live, whether a server module can carry them, what a real design system
// does to them — is downstream of that answer and not worth designing before it exists.
//
// So this file is deliberately the smallest thing that can answer it: the toolkit's own renderers, a
// design system of two lines, and a tree that could not be simpler. If the picture appears, the next
// questions are worth asking. If it does not, this file is deleted and #108 stands on its own.

private val previewRegistry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

// Not Material3DesignSystem: that lives in :kompot-ds-material-compose and depending on it from here
// would add a module to the experiment. A design system is a design system — if the preview renders
// with this one, it renders with a real one.
private val previewDesignSystem =
    object : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = MaterialTheme.colorScheme.primary

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle = MaterialTheme.typography.bodyLarge
    }

private val screen =
    ColumnComponent(
        id = "root",
        children =
            listOf(
                TextComponent(id = "title", text = "Catalogue"),
                ButtonComponent(id = "buy", text = "Buy", action = CloseAction),
            ),
    )

@Preview
@Composable
public fun KompotTreeIdePreview() {
    MaterialTheme {
        KompotPreview(
            // Through the wire, exactly as anywhere else this harness is used: the preview is of the
            // body, and encoding it here is the caller's step rather than the harness's.
            //
            // PolymorphicSerializer and not ColumnComponent.serializer(), which is the mistake this
            // file made on its first run: a concrete serialiser writes no discriminator for the root
            // it is handed, the root decodes to UnknownComponent, and the preview stopped and said so.
            // Which is the whole argument for previewing the body rather than the object, met here by
            // the file that exists to argue it.
            body = kompotJson().encodeToString(PolymorphicSerializer(KompotComponent::class), screen),
            registry = previewRegistry,
            designSystem = previewDesignSystem,
        )
    }
}
