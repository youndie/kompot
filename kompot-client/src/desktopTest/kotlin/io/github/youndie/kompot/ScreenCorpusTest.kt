package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.client.tck.ClientCorpusResources
import io.github.youndie.kompot.client.tck.ClientCorpusRunner
import io.github.youndie.kompot.client.tck.KompotFormClient
import io.github.youndie.kompot.form.FormController
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The cases of the client corpus that are about a screen (SPEC.md §16.4), held against this client as
// it draws: a real composition, the real chain (withUpdates), the real override store, and what the
// renderers were asked to draw read back by id. form-core answers the form cases in kompot-client-tck;
// it draws nothing, so the screen cases live here.
@OptIn(ExperimentalTestApi::class)
class ScreenCorpusTest {
    @Test
    fun `the Compose client answers the screen cases of the corpus`() =
        runDesktopComposeUiTest {
            val cases = ClientCorpusResources.cases().filter { it.screen != null }
            var mounted: ComposeScreenClient? by mutableStateOf(null)
            setContent {
                TestKompotTheme {
                    // A fresh composition per case: state left by one case is how a corpus stops meaning anything.
                    mounted?.let { client -> key(client) { client.Content() } }
                }
            }

            val report = ClientCorpusRunner(cases) { ComposeScreenClient(this) { mounted = it } }.run()

            assertTrue(cases.size >= 3, "only ${cases.size} screen cases were loaded")
            assertTrue(report.isClean, report.toString())
            assertEquals(emptyList(), report.unchecked, report.toString())
        }
}

// The adapter: values cross as JSON and are decoded with the engine's own Json, the way an answer is.
@OptIn(ExperimentalTestApi::class)
private class ComposeScreenClient(
    private val test: DesktopComposeUiTest,
    private val mount: (ComposeScreenClient) -> Unit,
) : KompotFormClient {
    private val json = kompotJson()
    private val overrides = KompotNodeOverrides()
    private val handed = mutableListOf<JsonObject>()
    private val handler =
        KompotActionHandler {}.withUpdates(overrides) { deeplink, history ->
            handed +=
                buildJsonObject {
                    put("deeplink", deeplink)
                    put("history", history)
                }
        }
    private var tree: KompotComponent? = null

    // What the renderers were last asked to draw, by id, while it stays on screen.
    private val drawn = mutableMapOf<String, KompotComponent>()
    private val registry =
        KompotRegistry(kompotCoreRenderers + kompotStandardRenderers).decorated { renderers ->
            renderers.mapValues { (_, renderer) ->
                // The registry keys renderers by class; this one wraps whatever it was given.
                @Suppress("UNCHECKED_CAST")
                Recording(renderer as KompotComponentRenderer<KompotComponent>, drawn)
            }
        }

    @Composable
    fun Content() {
        val root = tree ?: return
        CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
            KompotScreen(root, registry, testFormController(), handler)
        }
    }

    override fun show(screen: JsonObject): Boolean {
        tree = json.decodeFromJsonElement(PolymorphicSerializer(KompotComponent::class), screen)
        test.runOnIdle { mount(this) }
        test.waitForIdle()
        return true
    }

    override fun answer(action: JsonObject) {
        val decoded = json.decodeFromJsonElement(PolymorphicSerializer(KompotAction::class), action)
        test.runOnIdle { handler.handle(decoded) }
        test.waitForIdle()
    }

    override fun node(id: String): JsonObject? =
        drawn[id]?.let { json.encodeToJsonElement(PolymorphicSerializer(KompotComponent::class), it).jsonObject }

    override fun addresses(): List<JsonObject> = handed.toList()

    // A screen adapter: the form operations have nothing to act on, and no screen case calls them.
    override fun load(form: JsonObject) = unsupported()

    override fun set(
        fieldId: String,
        value: JsonObject,
    ) = unsupported()

    override fun blur(fieldId: String) = unsupported()

    override fun applyPatch(patch: JsonObject) = unsupported()

    override fun submit() = unsupported()

    override fun visibleFields(): List<String> = unsupported()

    override fun errors(): Map<String, String> = unsupported()

    override fun payload(): JsonObject? = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("a screen adapter: the form cases run in kompot-client-tck")
}

private class Recording(
    private val delegate: KompotComponentRenderer<KompotComponent>,
    private val drawn: MutableMap<String, KompotComponent>,
) : KompotComponentRenderer<KompotComponent> {
    @Composable
    override fun Render(
        component: KompotComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        DisposableEffect(component) {
            drawn[component.id] = component
            onDispose { if (drawn[component.id] === component) drawn.remove(component.id) }
        }
        delegate.Render(component, actionHandler, formController)
    }
}
