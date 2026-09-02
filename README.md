# kompot

[![kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![kompot-core](https://reposilite.kotlin.website/api/badge/latest/snapshots/io/github/youndie/kompot-core?name=kompot-core&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/io/github/youndie/kompot-core)
[![what a consumer gets](https://raw.githubusercontent.com/youndie/kompot/badges/io.github.youndie.kompot-core.svg)](https://github.com/youndie/proba)

![jvm](https://img.shields.io/badge/jvm-DB413D?style=flat)
![android](https://img.shields.io/badge/android-3DDC84?style=flat)
![ios](https://img.shields.io/badge/ios-CDCDCD?style=flat)
![wasmJs](https://img.shields.io/badge/wasmJs-624FE8?style=flat)

**a backend-driven UI toolkit for Kotlin Multiplatform** — the server describes a screen as a tree
of components, the client renders it, and a new screen ships without a new client release

> 🧃 one annotation → the component is on the wire and in the renderer registry

### 🤔 What it is

Backend-driven UI is easy to start and hard to keep honest. The usual failure is a toolkit that
knows your components: a sealed hierarchy of every widget the product happens to have, a client that
must be released whenever the server learns a new one, and a serialiser that quietly turns an
unfamiliar type into a crash.

Kompot is built the other way round. The core knows about a tree, actions, modifiers and tokens —
and nothing about what a "product card" or a "story" is. Everything concrete arrives as a plug-in
module, and an unknown type degrades to a placeholder instead of taking the screen down.

- **open contracts, not sealed sets** — `KompotComponent`, `KompotAction`, `FieldValue` and
  `ValidationRule` are interfaces; the set of concrete types is assembled by the application;
- **generated registration** — mark a component or a renderer with `@KompotComponentMarker` and KSP
  writes its polymorphic registration and renderer entry, so no list has to be maintained by hand;
- **open string tokens** — a backend may send `ColorToken("promo_gold")` with no client release; a
  server-driven theme (`kompot-theme`) can also say what to paint it with;
- **forms that stay client-side** — validation, visibility conditions and cross-field rules run
  locally, and only a server-relevant change asks the backend for a patch;
- **live updates** — a screen can name its own update channel, so updates stay per-user rather than
  broadcast to everyone on the same page, and the same channel works across several server instances;
- **multi-step flows as pure functions** — a wizard graph is `(session, transition, draft) -> session`,
  so branching is covered by unit tests with no HTTP, no database and no UI in sight;
- **it degrades rather than breaks** — an unknown component, an unknown action or a malformed theme
  token costs a widget or some styling, never the screen.

### 🧪 Read back from the repository

Every release is checked from the other side by [proba](https://github.com/youndie/proba): it resolves
the published coordinates the way a stranger's build does and reports what a consumer actually
receives.

The distinction is the point. A module whose public API returns a type it declares as
`implementation` compiles, tests and publishes green here, and hands a consumer a signature they
cannot name; a file can go up under a name carrying a version that was never released. Both happened,
both were found from the other side, and neither is visible from this one.

The third badge above says what it found, in a word — `clean`, `1 suspicion`, `1 unchecked` — and the
colour only agrees with it. It is a file written by the run that published the version beside it, not
a service answering when somebody looks: a publication never changes, so neither can the answer. The
other checked coordinates report the same way in the job's summary; the readme carries the one a
consumer starts from.

`1 unchecked` is not `clean`: a check that could not run is not a check that passed. A suspicion is a
shape the metadata cannot tell apart from the correct case — on `wasmJs`, for instance, where a jvm
consumer build cannot confirm what the metadata suspects.

From this side the same question is asked by the compiler: every module is built with `explicitApi`,
so a declaration reaches you because somebody wrote `public`, not because nobody wrote anything.

### 📦 Modules

| module | what for | depends on |
| --- | --- | --- |
| `kompot-core` | the tree, actions, modifiers, design-system tokens | — |
| `kompot-bom` | the platform: one version for every coordinate this build publishes | — |
| `kompot-registry-annotations` | the `@KompotComponentMarker` annotation | — |
| `kompot-registry-processor` | the KSP processor that generates registrations | — |
| `kompot-standard` | the standard component set: text, containers, lists, pagination | core |
| `kompot-forms` | form components over `form-core` | core, form-core |
| `kompot-theme` | server-driven theming, no UI toolkit | core |
| `kompot-ds-material` | the reference Material3 token set: constants a server and a client share | core |
| `kompot-ktor` | Ktor helpers for polymorphic roots, ETags, experiment headers | core, experiments-core |
| `kompot-realtime` | the live-update channel contract | core |
| `kompot-realtime-server` | delivery to one instance's subscribers plus the bus contract between instances | kompot-realtime |
| `kompot-realtime-redis` | the Redis pub/sub bus, for more than one instance | kompot-realtime-server |
| `kompot-images` | an image by URL, as a component plug-in | core |
| `kompot-auth` | the one action that hands the client a new session | core |
| `kompot-commands` | the one action that acts on a single item of a list, with no form around it | core, form-core |
| `kompot-navigation` | the navigation graph of plain, code-free screens | — |
| `wizard-core` | the step machine of a multi-step flow, as a pure function | — |
| `kompot-wizard` | the wire side of that flow: step screen, transitions, resume request | core, form-core, wizard-core |
| `form-core` | form state: validation, visibility, cross-field rules, patches | — |
| `form-standard` | the standard field set over form-core: text, amount, checkbox, autocomplete, selection | form-core |
| `kompot-forms-standard` | the glue: one call declares a field and draws the component that fills it | kompot-forms, form-standard |
| `experiments-core` | deterministic A/B assignment plus its header codec | — |
| `kompot-spec` | the wire specification: schema generator, validator, and the spec module of every module above | all of them |
| `kompot-tck` | the conformance kit: walks a running server over HTTP and checks the rules a schema cannot express | kompot-spec |
| `kompot-client-tck` | the case corpus: input and observable outcome, holding a CLIENT to the rules only it carries | — |
| `kompot-swift-interop` | the Swift bridge: what the Kotlin/Native ObjC export drops — reified calls, suspend contracts, value-class tokens | core, standard, forms, wizard |
| `kompot-client` | the Compose client: a registry keyed by wire type, the core renderers, live updates, impression tracking | core, standard, forms |
| `kompot-forms-client`, `kompot-wizard-client`, `kompot-images-client-coil` | the renderers of the form, wizard and image plug-ins | kompot-client |
| `kompot-theme-client`, `kompot-ds-material-compose` | the Compose side of a server-driven theme, and the Material3 design system tokens resolve through | kompot-client, kompot-theme |
| `kompot-preview` | a response body drawn by the real renderers, so a server can see the screen it built | kompot-client, kompot-forms |
| `kompot-client-cache` | offline-first screen cache: the store contract and a cache-first provider with ETag revalidation | core |
| `kompot-analytics` | tracking contracts for screens, actions and form outcomes | — |

`form-core`, `experiments-core` and `wizard-core` are usable on their own and know nothing about
Kompot components: one manages form state, one assigns variants, one walks a graph of steps. They
keep their names for that reason.

The realtime pair is worth separating in your head from `kompot-realtime`, which is only the frame
contract. `kompot-realtime-server` delivers to the subscribers of one process and defines the bus
between processes; its default bus is in-memory, so a single-instance application needs no
infrastructure. `kompot-realtime-redis` is the bus for the multi-instance case, and it is pub/sub
without delivery guarantees on purpose: a component update is a thing you can afford to lose, since
the client gets current state with its next screen request anyway.

### 🧭 Laying out a screen, and reading it against a canvas

[`skills/kompot-layout/SKILL.md`](skills/kompot-layout/SKILL.md) is the method: what travels on the
wire and what stays in the client, how a screen is built on the server and drawn through the
registry, how goldens and fixtures are kept honest, and the loop — measure before looking — that
takes a screen from a canvas to the wire. It is written as a Claude Code skill; link it into
`~/.claude/skills/kompot-layout` to have it load when a screen is being laid out.

[`tools/canvas`](tools/canvas/README.md) are the measuring instruments: render a canvas artboard
and diff its text geometry against a Compose frame, inventory a canvas's tokens and generate the
design system from the named file, draw a server's recorded screens as a canvas in the wire's own
vocabulary and read such a canvas back into a tree.

### 📐 The wire specification

`kompot-spec` generates a JSON Schema for every protocol module out of the very SerialDescriptors
kotlinx.serialization encodes a response with, so a schema cannot fall quietly behind the types. The
generated files are committed in [`kompot-spec/schema`](kompot-spec/schema) and the rules a schema
cannot express — degradation, the two kinds of extensibility, form connectivity, pagination,
transport — are written out in [`kompot-spec/SPEC.md`](kompot-spec/SPEC.md), addressed to somebody
implementing a server on another stack.

Both travel in the artefact — the schemas and the document — because a rule of §9 carries an id a
conformance case names, and an id whose text lives only in this repository is a reference with
nothing behind it for anybody reading from another language:

```kotlin
val rules = KompotSpecResources(root = "kompot-spec").rules()
rules["9.4.3"] // "Ошибка, поднятая до того, как поле скрылось, перестаёт действовать вместе с полем."
```

The closed list of types is a property of a **build**, not of the toolkit: an application assembles
its own spec from these modules plus its own, and gets its own profile. The thirteen toolkit schemas
and the profile beside them come out byte-identical either way.

```kotlin
val schemas = KompotSpec.generateAll(KompotToolkitSpec.modules + myComponentsSpecModule())
val profile = KompotSpec.profile(schemas)
```

`kompot-tck` is the other half: it points at a **running** server and checks what a schema cannot
express — ids unique within a tree, a form's screen and schema agreeing on fieldId, a 304 on a
repeated ETag, pagination that terminates, a 401 without a token, the idempotency contract. It reads
endpoint kinds out of your OpenAPI document and never assumes an address, so it runs against an
implementation on any stack:

```kotlin
val report = TckRunner(RemoteTckTransport("http://localhost:5000"), TckConfig(schemas, openApi)).run()
check(report.isClean) { report.toString() }
```

The report prints how many targets each check visited. A check that found nothing to apply to passes
silently, and that is the commonest way to end up with a conformance kit that proves nothing.

### 🔌 Installation

Every module is published under one version, so take the version once, from the platform, and name no
version anywhere else. The badge at the top of this file shows the latest one — substitute it below.

```kotlin
repositories {
    maven("https://reposilite.kotlin.website/snapshots")
}

val kompotVersion = "0.36.2.115"

dependencies {
    implementation(platform("io.github.youndie:kompot-bom:$kompotVersion"))

    implementation("io.github.youndie:kompot-core")
    implementation("io.github.youndie:kompot-standard")
    implementation("io.github.youndie:kompot-ktor")
}
```

The platform is worth using rather than repeating the version, and not only for brevity. A version
carries the CI run number on its tail, so **any two publishes differ** — and
`kompot-core:0.19.0.26` beside `kompot-client:0.19.0.27` resolves quietly into a combination nobody
ever built or tested. Through the platform that combination cannot be written down. It constrains
every coordinate this build publishes, including the per-target ones a Kotlin Multiplatform module
adds beside its root (`kompot-core-jvm`, `kompot-core-iosarm64`, and so on), so a consumer naming one
of those directly is covered too.

To have registrations generated, add KSP to the module that declares components and give it a tag
unique to that module:

```kotlin
plugins { id("com.google.devtools.ksp") }

dependencies {
    implementation("io.github.youndie:kompot-registry-annotations")
    ksp("io.github.youndie:kompot-registry-processor")
}

ksp { arg("kompotModuleTag", "Catalogue") }
```

The tag must be unique because generated files land in one package: two modules sharing a tag would
generate objects of the same name and collide in the consumer's build. The generated code compiles
into the declaring module's own artifact, so **a consumer never applies KSP** — it just imports
`generatedCatalogueSerializersModule`.

**A component and its renderer belong in different modules once you have a server.** A renderer needs
Compose and a server does not have it, so a component declared beside its renderer is a component the
server cannot construct — which is the one thing a server-driven component exists for. Declare the
component in a module both sides depend on, the renderer in the Compose one, and give each its own
tag; the renderer carries its component in its type argument, so the generated registry pairs them
across the module boundary:

```kotlin
// :catalogue-wire — no Compose, and the server depends on it
@Serializable @SerialName("product_card") @KompotComponentMarker
data class ProductCardComponent(override val id: String, val title: String) : KompotComponent

// :catalogue-ui — ksp { arg("kompotModuleTag", "CatalogueUi") }
@KompotComponentMarker
class ProductCardRenderer : KompotComponentRenderer<ProductCardComponent> { … }
```

`kompot-forms` and `kompot-forms-client` are that pair, which is why the split is exercised on every
build of this repository rather than only described here.

### ✍️ What it looks like

A component is a plain serialisable data class:

```kotlin
@Serializable
@SerialName("product_card")
@KompotComponentMarker
data class ProductCardComponent(
    override val id: String,
    val title: String,
    val onClick: KompotAction? = null,
) : KompotComponent
```

The server builds a tree and responds with it through a helper rather than `call.respond`:

```kotlin
get("/catalogue") {
    call.respondKompotComponent(
        column {
            text("Catalogue", style = TypographyToken("title_large"))
            items.forEach { productCard(title = it.title, onClick = navigate(it.url)) }
        },
    )
}
```

`respondKompotComponent` exists because a plain `call.respond(component)` resolves the serialiser
from the concrete runtime class and drops the `"type"` discriminator on the ROOT of the tree — the
client then receives an unknown component. Nested children are unaffected, which is what makes the
bug so easy to miss.

### 👁️ Seeing the screen you just built

Writing a screen on the server is otherwise writing a tree blind: the only way to look at it is to
run a client against it. `kompot-preview` draws the **body an endpoint returns** with the real
renderers, and `@Preview` beside it puts that picture in the IDE next to the code that builds it.

```kotlin
@Preview
@Composable
fun CheckoutPreview() {
    KompotPreview(
        body = myJson.encodeToString(KompotFormResponse.serializer(), checkoutScreen()),
        registry = myRegistry,
        designSystem = myDesignSystem,
        // A form is not one picture: empty, filled and showing every error are three.
        state = KompotPreviewState(allFieldsChanged = true),
        json = myJson,
    )
}
```

The body and not the component in hand, and that is the point rather than an inconvenience: a plain
`call.respond` drops the `"type"` discriminator on the ROOT of a tree, so a screen that renders
perfectly from the object in memory degrades to a placeholder in front of a person. A preview taken
from the object photographs a working screen that does not work. For the same reason a missing
renderer stops the preview instead of drawing the grey placeholder — recorded into a screenshot golden
it would become the screen's expected appearance.

The same call is what a screenshot test photographs, so a preview and a golden are one input and two
checks. Note that an IDE preview renders through skiko, whose host-native half comes with
`compose.desktop.currentOs`: it is present in an application module and cannot be in a published one,
since it would pin the host in the POM.

### 🚫 What it does not do

- **it does not ship YOUR renderers** — the Compose renderers of the standard, form, wizard and image
  components are here, and so is a Material3 design system; what stays yours is the renderer of a
  component you invented. The registry is open exactly the way the wire types are. The SwiftUI side is
  yours too: `kompot-swift-interop` is not a renderer but the handful of non-generic functions Swift
  needs because the Kotlin/Native ObjC export drops reified calls, suspend contracts and value
  classes;
- **it does not choose a transport** — the live-update contract is here, the SSE or WebSocket
  implementation is yours;
- **it does not validate your business rules** — `form-core` covers what a form can decide locally;
  limits and balances belong to the server, and the client only highlights the field it names;
- **it does not assemble your component set for you** — the application composes its own
  `SerializersModule` from the generated pieces, which is precisely why the library never has to
  know what your product contains.

### 🎯 Targets

Every protocol module publishes for **JVM, Android, the three iOS targets and `wasmJs`**, and every
Compose client module for the same set minus one: **`iosX64` is not reachable for anything that
depends on Compose**, because `compose.runtime` published its last artefact for it at
`1.11.0-alpha01`. An Intel simulator is where that ends; a device and an Apple-silicon simulator are
not.

So the same screens are drawn on a desktop, on a phone, on an iPhone and in a browser without a line
of the wire changing. There is no `expect`/`actual` anywhere in the toolkit — the modules are common
code — which is why a target costs a declaration rather than a port.

The Compose half is built against **Compose Multiplatform 1.11.1** and **material3 1.11.0-alpha07**,
and the second number is not sloppiness: that line has no stable material3 at all. It is worth
knowing because mixing lines fails late — a consumer on 1.12.0 resolves foundation and runtime to
1.12.0 while material3 stays where these modules put it, and the pair compiles, starts, and throws
`AbstractMethodError` at the first screen with a text field on it.

Android is the newest of them, and it is worth saying what its absence used to do rather than only
that it is there. Nothing failed: an Android consumer resolved the **desktop** variant, because
Gradle will hand an `androidJvm` consumer a `jvm` artefact when the producer publishes no android
one. It compiled, and the app then carried a client built against desktop Compose beside its own
android Compose. Now the same build receives `kompot-client-android` and an `.aar`.

Three modules stay off the browser, off iOS and off Android on purpose, and it is worth naming them
so a deployment can plan around it rather than discover it — a deployment plans around a documented
absence and stumbles into an undocumented one:

| module | why |
|---|---|
| `kompot-ktor` | server helpers over Ktor's server engine |
| `kompot-realtime-server` | delivery to one instance's subscribers |
| `kompot-forms-standard` | a DSL that builds a form **on the server** |

### 🛠️ Building

```bash
./gradlew build
```

On CI (Linux) that is the whole story: Kotlin/Native cross-compiles the Apple klibs there, and the
simulator test tasks do not exist. On a Mac without an iOS simulator runtime installed the same
command fails with "Xcode does not support simulator tests for ios_simulator_arm64" — the failure is
the missing runtime, not the code. Either install one through Xcode, or skip those tasks:

```bash
./gradlew build -x iosSimulatorArm64Test -x iosX64Test
```

Java 17 — both the toolchain the modules compile against and the floor their metadata declares, so a
consumer on 17 gets a build that resolves rather than an `UnsupportedClassVersionError` at class
loading. It is 17 for every module at once, and that part is a Gradle requirement rather than
tidiness: where `org.gradle.jvm.version` is present, a module on 17 cannot be built against a
dependency on 25, so it is all of them or none.

A toolchain rather than `jvmTarget` alone, for the same reason: `jvmTarget` asks for older bytecode
while still compiling against the newest JDK's class library, so a call to something added in 21
compiles and then fails on a 17 runtime.

### 📄 License

MIT.
