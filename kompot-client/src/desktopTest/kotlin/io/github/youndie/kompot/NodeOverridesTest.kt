package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test

// One store of node overrides per screen (SPEC.md §4.4): live frames write into it, RenderNode reads it,
// and a whole tree that arrives again is the truth about every node in it.
@OptIn(ExperimentalTestApi::class)
class NodeOverridesTest {
    private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    private fun board(card: String) =
        ColumnComponent(
            id = "board",
            children = listOf(TextComponent(id = "card", text = card), TextComponent(id = "footer", text = "footer")),
        )

    /**
     * A frame that arrived before a refresh does not cover the node the refresh brought.
     *
     * The provider kept its frames under remember(topic), apart from the tree: a refresh of the same
     * screen on the same topic left the map as it was, and the card went on showing what the frame
     * said while the server had since sent a newer card — §4.4's "the version sent again is applied"
     * broken for exactly the screens that have a channel.
     */
    @Test
    fun `a refresh after a live frame shows the node from the new tree`() =
        runDesktopComposeUiTest {
            val frames = MutableSharedFlow<UpdateComponentMessage>(extraBufferCapacity = 8)
            val source = KompotRealtimeSource { frames }

            setContent {
                var tree by remember { mutableStateOf(board("card v1")) }
                val scope = rememberCoroutineScope()
                val handler = remember { KompotActionHandler {}.withRefresh(scope) { tree = board("card v2") } }
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides registry) {
                        KompotRealtimeProvider(topic = "board:user1", source = source, content = {
                            Column {
                                Button(onClick = { handler.handle(RefreshAction) }) { Text("refresh") }
                                KompotScreen(tree, registry, testFormController(), handler)
                            }
                        })
                    }
                }
            }
            waitForIdle()

            runOnIdle {
                frames.tryEmit(
                    UpdateComponentMessage("card", TextComponent(id = "card", text = "card from the frame")),
                )
            }
            waitForIdle()
            // The control: the frame did land, so its absence below is the refresh's doing.
            onNodeWithText("card from the frame").assertExists()

            onNodeWithText("refresh").performClick()
            waitForIdle()
            onNodeWithText("card v2").assertExists()
            onNodeWithText("card from the frame").assertDoesNotExist()
        }

    private fun nested(inner: String) =
        ColumnComponent(
            id = "board",
            children =
                listOf(
                    ColumnComponent(id = "group", children = listOf(TextComponent(id = "inner", text = inner))),
                    TextComponent(id = "footer", text = "footer"),
                ),
        )

    private fun DesktopComposeUiTest.screen(
        overrides: KompotNodeOverrides,
        tree: () -> KompotComponent,
    ) {
        setContent {
            TestKompotTheme {
                CompositionLocalProvider(
                    LocalKompotRegistry provides registry,
                    LocalKompotNodeOverrides provides overrides,
                ) {
                    KompotScreen(tree(), registry, testFormController(), recordingActionHandler())
                }
            }
        }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.write(
        overrides: KompotNodeOverrides,
        component: KompotComponent,
    ) {
        runOnIdle { overrides.override(component.id, component) }
        waitForIdle()
    }

    /**
     * An override of a node brings its children whole: an older override of one of them does not
     * cover the child the new version brought.
     */
    @Test
    fun `an override of a node drops the overrides written inside its previous version`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            screen(overrides) { nested("inner v1") }

            write(overrides, TextComponent(id = "inner", text = "inner from a frame"))
            onNodeWithText("inner from a frame").assertExists()

            write(
                overrides,
                ColumnComponent(
                    id = "group",
                    children = listOf(TextComponent(id = "inner", text = "inner from the group")),
                ),
            )
            onNodeWithText("inner from the group").assertExists()
            onNodeWithText("inner from a frame").assertDoesNotExist()

            // The other half: an override written AFTER the group's does apply inside it.
            write(overrides, TextComponent(id = "inner", text = "inner after the group"))
            onNodeWithText("inner after the group").assertExists()
        }

    /**
     * An override for an id the tree does not have is ignored (§10.2) — also when a later override
     * brings a node with that id: it was written against a tree that had no such node.
     */
    @Test
    fun `an id the tree does not have is ignored even once a later override brings that node`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            screen(overrides) { nested("inner v1") }

            write(overrides, TextComponent(id = "late", text = "late from a stray frame"))
            onNodeWithText("late from a stray frame").assertDoesNotExist()
            onNodeWithText("inner v1").assertExists()

            write(
                overrides,
                ColumnComponent(
                    id = "group",
                    children = listOf(TextComponent(id = "late", text = "late from the group")),
                ),
            )
            onNodeWithText("late from the group").assertExists()
            onNodeWithText("late from a stray frame").assertDoesNotExist()
        }

    /**
     * A whole tree drops the overrides written before it — and an equal tree handed over with no
     * arrival is not a new one, so a recomposition that hands the screen the same tree again does not
     * undo a frame. An equal tree that does arrive is TreeArrivalTest's.
     */
    @Test
    fun `a new tree drops the overrides and an equal one keeps them`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            var tree by mutableStateOf<KompotComponent>(nested("inner v1"))
            screen(overrides) { tree }

            write(overrides, TextComponent(id = "inner", text = "inner from a frame"))
            runOnIdle { tree = nested("inner v1") }
            waitForIdle()
            onNodeWithText("inner from a frame").assertExists()

            runOnIdle { tree = nested("inner v2") }
            waitForIdle()
            onNodeWithText("inner v2").assertExists()
            onNodeWithText("inner from a frame").assertDoesNotExist()
        }
}
