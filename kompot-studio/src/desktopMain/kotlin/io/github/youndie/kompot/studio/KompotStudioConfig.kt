package io.github.youndie.kompot.studio

import androidx.compose.runtime.Composable
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.standard.KompotPageLoader
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

// Read from the jar rather than from a checkout: that is how a consumer's build reads them too, and a
// default that read them off disk would work only in this repository.
private fun toolkitSchemas(): Map<String, JsonObject> = KompotSpecResources("kompot-spec").schemas()
