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
// design system of two lines, and a tree that could not be simpler. The answer is yes — IntelliJ
// composes it and draws the screen.
//
// TWO THINGS ABOUT THE CLASSPATH AND SKIKO, learnt the long way and worth separating, because they
// look like one problem and are not.
//
// The first is real and permanent: an IDE preview renders through skiko, whose HOST-NATIVE half
// arrives with compose.desktop.currentOs — and currentOs cannot live in a published source set, since
// it would pin the host in this module's POM. So this source set carries skiko-awt without
// skiko-awt-runtime-<host>, and the preview finds the library only where a machine already has it
// cached in ~/.skiko. Copying this file into a module of your own — an application module has
// currentOs anyway — is the intended use and renders on a clean machine too.
//
// The second looks like the first and is not caused by it: "Could not initialize class
// org.jetbrains.skia.Surface", under it a FileLockInterruptionException from skiko's library loader.
// Moving this file to a source set that DOES have currentOs changed nothing, which is what rules the
// classpath out. That exception is also thrown when the calling thread's interrupt flag was already
// set, and the preview host renders on one long-lived thread — so one cancelled frame poisons every
// later one, and the class stays broken for the life of that JVM. It reads as "the preview stopped
// working" when what happened is "it failed once". The cure is restarting the preview process, not
// changing anything here.

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

// internal rather than private: the test beside this file asserts that what the tree SAYS is what got
// drawn, and derives the strings from here rather than repeating them. The label of a button in an
// example is exactly the kind of thing somebody retypes — to watch the preview redraw, say — and a
// test that pinned a copy of it would fail on a cosmetic edit and teach people to ignore it.
internal val previewScreen =
    ColumnComponent(
        id = "root",
        children =
            listOf(
                TextComponent(id = "title", text = "Catalogue"),
                ButtonComponent(id = "buy", text = "Buy this", action = CloseAction),
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
            body = kompotJson().encodeToString(PolymorphicSerializer(KompotComponent::class), previewScreen),
            registry = previewRegistry,
            designSystem = previewDesignSystem,
        )
    }
}
