package io.github.youndie.kompot.studio

import androidx.compose.runtime.Composable
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.theme.KompotTheme
import java.nio.file.Path
import io.github.youndie.kompot.studio.source.ScreenSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// WHAT A CONSUMER HANDS THE STUDIO. Everything here belongs to the deployment being previewed; the
// studio itself knows none of it in advance, and that is the whole design rather than politeness.
//
// A brand is the reason. It is not a KompotTheme: a client resolves shapes and font families of its
// own — konekt keeps them in KonektShapeScale.byBrand and KonektTypography — and a studio that
// assembled a brand out of the served theme alone would be photographing a second client that nobody
// ships. So the consumer hands over the COMPOSITION and the studio only ever asks it for "brand X,
// dark": that is `frame`.
public class KompotStudioConfig(
    // The renderers. A deployment previewing its own screens is previewing its own components, so a
    // registry assembled here would draw a different product. `frame` may install a registry of its
    // own through LocalKompotRegistry — konekt's screenshot frame already does — and that one wins;
    // this is what a frame which installs nothing falls back to.
    public val registry: KompotRegistry,
    // THE SAME Json the client decodes with. A preview that can decode more than its client can is a
    // picture of a screen the client cannot show; one that can decode less fails on a body that works.
    public val json: Json = kompotJson(),
    // The brand composition, and the centre of this contract. The studio calls it and reads back
    // LocalKompotDesignSystem and LocalKompotRegistry from inside — so a consumer's existing
    // screenshot frame is usable unchanged, and a frame that installs neither still renders.
    public val frame: KompotStudioFrame = kompotStudioFrame(),
    // NOTE: a default frame built from `themes` cannot be the default of `frame` above — a constructor
    // parameter cannot read one declared after it. A project with no frame of its own passes
    // `kompotStudioFrame(themes)` explicitly, and `kompotThemesFrom(dir)` is what fills both.
    // The names `frame` understands. The studio does not interpret them: it shows them and passes
    // whichever is selected straight back.
    public val brands: List<String> = emptyList(),
    // The vocabulary the body is checked against: the toolkit's own schemas plus the deployment's
    // component modules. The default is the toolkit's, read from the classpath the way a consumer
    // reads them, so a studio with no configuration still lints against a closed list of types.
    public val schemas: Map<String, JsonObject> = toolkitSchemas(),
    // Where the bodies are: recordings on disk, a directory of them, a running server with its own
    // NavigationGraph. Empty means the window opens on the body it was handed and watches nothing —
    // which is what the toolkit's own demo does, having no deployment to read from.
    public val sources: List<ScreenSource> = emptyList(),
    // How a paginated list gets its next page. Null — the default — means it does not: the body says
    // so loudly rather than showing a list that ends where it does not. A deployment that wants to
    // scroll one in the window passes its own, or a stub answering an empty page.
    public val pageLoader: KompotPageLoader? = null,
    // Where a body fetched from a server is written down when somebody saves it. This is the step a
    // deployment does by hand today — copy the response into `/recorded/*.json` — and it is worth
    // having because a recording is what a screenshot test replays. Null means an HTTP body cannot be
    // saved: there is nowhere to put it, and inventing a directory would scatter fixtures.
    public val recordingsDirectory: Path? = null,
    // One fully filled instance per wire type — the dictionary a deployment already keeps beside its
    // components, usually for a schema golden or a coverage check. It is a Storybook nobody had a
    // window for: pairs of (wire name, component), so a type with no sample is visible AS a gap
    // rather than silently absent.
    public val samples: List<Pair<String, KompotComponent>> = emptyList(),
    // The open words a field of a component accepts: `usage_counter_card.state` to every state that
    // draws differently. The protocol calls these open on purpose — an unknown word draws the neutral
    // thing — so nothing can derive this list, and a deployment that keeps one keeps it in Kotlin.
    // With it, "every state of every component" stops being a picture somebody redraws.
    public val vocabulary: Map<String, Map<String, Set<String>>> = emptyMap(),
    // The brand kits themselves, by the same names as `brands`. The default frame already needs them;
    // the token check needs them for a different reason — to say which token no kit names, and in
    // which palette. Empty means that check does not run: a deployment whose frame builds its kits in
    // code has nothing to hand over, and inventing a set would report every token as missing.
    public val themes: Map<String, KompotTheme> = emptyMap(),
    // Where this build keeps its goldens. viddik's convention is a `snapshots` directory beside the
    // tests that record them, and the studio only ever READS from it plus writes where asked — it does
    // not run viddikVerify and does not decide for a deployment what its expected picture is.
    public val snapshotsDirectory: Path? = null,
    // What a golden is called. The consumer's, for the same reason `frame` is: viddik names a file
    // "<group>_<name>.png" and only the deployment knows what its groups are — konekt's `brand-a` is
    // `Brand_A.png`, and no rule this module could invent would guess that.
    public val goldenName: (brand: String?, dark: Boolean, screen: String) -> String = ::defaultGoldenName,
    // Where a form's rules and conditions keep the fieldId they point at, by the wire type that
    // carries them: "required_if" to "fieldId", and whatever a deployment adds beside them. The
    // toolkit cannot know — a rule type is a deployment's to invent — so an empty map means the
    // cross-reference half of the form check simply does not run.
    public val crossReferenceKeys: Map<String, String> = emptyMap(),
    // Wire types a DEPLOYMENT adds on top of the profile. Declared ones pass the check without their
    // shape being validated — safe, because an unfamiliar type degrades by protocol; undeclared ones
    // stay violations, or the check would stop meaning anything.
    public val extensionTypes: Set<String> = emptySet(),
)

// The composition a brand lives in. A function and not a data class of colours: the studio must be
// able to ask for a brand without knowing what a brand is made of.
public typealias KompotStudioFrame =
    @Composable (brand: String?, dark: Boolean, content: @Composable () -> Unit) -> Unit

// Screen_Home.png, Screen_Home_Dark.png, BrandA_Home.png. A shape rather than a convention: it is
// what a project with no golden naming of its own gets, and it is visible in the window so that a
// mismatch with an existing file is obvious before anybody clicks compare.
public fun defaultGoldenName(
    brand: String?,
    dark: Boolean,
    screen: String,
): String {
    fun camel(text: String) =
        text.split('-', '_', ' ', '/', '.')
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    val group = brand?.let(::camel)?.ifEmpty { null } ?: "Screen"
    return "${group}_${camel(screen)}${if (dark) "_Dark" else ""}.png"
}

// Read from the jar rather than from a checkout: that is how a consumer's build reads them too, and a
// default that read them off disk would work only in this repository.
private fun toolkitSchemas(): Map<String, JsonObject> = KompotSpecResources("kompot-spec").schemas()
