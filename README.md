# kompot

[![kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![kompot-core](https://reposilite.kotlin.website/api/badge/latest/snapshots/io/github/youndie/kompot-core?name=snapshots&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/io/github/youndie/kompot-core)
[![kompot-standard](https://reposilite.kotlin.website/api/badge/latest/snapshots/io/github/youndie/kompot-standard?name=snapshots&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/io/github/youndie/kompot-standard)
[![kompot-ktor](https://reposilite.kotlin.website/api/badge/latest/snapshots/io/github/youndie/kompot-ktor?name=snapshots&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/io/github/youndie/kompot-ktor)

[![kompot-core, from a consumer](https://raw.githubusercontent.com/youndie/kompot/badges/io.github.youndie.kompot-core.svg)](https://github.com/youndie/proba)
[![kompot-client, from a consumer](https://raw.githubusercontent.com/youndie/kompot/badges/io.github.youndie.kompot-client.svg)](https://github.com/youndie/proba)
[![kompot-client-tck, from a consumer](https://raw.githubusercontent.com/youndie/kompot/badges/io.github.youndie.kompot-client-tck.svg)](https://github.com/youndie/proba)

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

### 🧪 The second row of badges

Those say what somebody who is not us gets when they add the library — read from the published
artefact by [proba](https://github.com/youndie/proba) after every release, not from this build.

The distinction is the point. A module whose public API returns a type it declares as
`implementation` compiles, tests and publishes green here, and hands a consumer a signature they
cannot name; a file can go up under a name carrying a version that was never released. Both happened,
both were found from the other side, and neither is visible from this one.

The state is a **word** — `clean`, `2 suspicions`, `1 unchecked` — and the colour only agrees with it.
`1 unchecked` is not `clean`: a check that could not run is not a check that passed.

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

### 📐 The wire specification

`kompot-spec` generates a JSON Schema for every protocol module out of the very SerialDescriptors
kotlinx.serialization encodes a response with, so a schema cannot fall quietly behind the types. The
generated files are committed in [`kompot-spec/schema`](kompot-spec/schema) and the rules a schema
cannot express — degradation, the two kinds of extensibility, form connectivity, pagination,
transport — are written out in [`kompot-spec/SPEC.md`](kompot-spec/SPEC.md), addressed to somebody
implementing a server on another stack.

The closed list of types is a property of a **build**, not of the toolkit: an application assembles
its own spec from these modules plus its own, and gets its own profile. The ten toolkit files come
out byte-identical either way.

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

val kompotVersion = "0.29.0.56"

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

Every protocol and client module publishes for **JVM, the three iOS targets and `wasmJs`**, so the
same screens are drawn on a desktop, on a phone and in a browser without a line of the wire changing.
There is no `expect`/`actual` anywhere in the toolkit — the modules are common code — which is why a
target costs a declaration rather than a port.

Three modules stay off the browser and off iOS on purpose, and it is worth naming them so a
deployment can plan around it rather than discover it:

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

Java 25 for every module at once — not tidiness but a Gradle requirement: it tags variants with the
`org.gradle.jvm.version` attribute and refuses to build a module on 21 against a dependency on 25.

### 📄 License

MIT.
