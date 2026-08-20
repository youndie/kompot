# kompot

[![kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![kompot-core](https://reposilite.kotlin.website/api/badge/latest/snapshots/io/github/youndie/kompot-core?name=snapshots&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/io/github/youndie/kompot-core)
[![kompot-standard](https://reposilite.kotlin.website/api/badge/latest/snapshots/io/github/youndie/kompot-standard?name=snapshots&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/io/github/youndie/kompot-standard)
[![kompot-ktor](https://reposilite.kotlin.website/api/badge/latest/snapshots/io/github/youndie/kompot-ktor?name=snapshots&color=40c14a&prefix=v)](https://reposilite.kotlin.website/#/snapshots/io/github/youndie/kompot-ktor)

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

### 📦 Modules

| module | what for | depends on |
| --- | --- | --- |
| `kompot-core` | the tree, actions, modifiers, design-system tokens | — |
| `kompot-registry-annotations` | the `@KompotComponentMarker` annotation | — |
| `kompot-registry-processor` | the KSP processor that generates registrations | — |
| `kompot-standard` | the standard component set: text, containers, lists, pagination | core |
| `kompot-forms` | form components over `form-core` | core, form-core |
| `kompot-theme` | server-driven theming, no UI toolkit | core |
| `kompot-ktor` | Ktor helpers for polymorphic roots, ETags, experiment headers | core, experiments-core |
| `kompot-realtime` | the live-update channel contract | core |
| `kompot-realtime-server` | delivery to one instance's subscribers plus the bus contract between instances | kompot-realtime |
| `kompot-realtime-redis` | the Redis pub/sub bus, for more than one instance | kompot-realtime-server |
| `kompot-images` | an image by URL, as a component plug-in | core |
| `kompot-auth` | the one action that hands the client a new session | core |
| `kompot-navigation` | the navigation graph of plain, code-free screens | — |
| `wizard-core` | the step machine of a multi-step flow, as a pure function | — |
| `kompot-wizard` | the wire side of that flow: step screen, transitions, resume request | core, form-core, wizard-core |
| `form-core` | form state: validation, visibility, cross-field rules, patches | — |
| `experiments-core` | deterministic A/B assignment plus its header codec | — |

`form-core`, `experiments-core` and `wizard-core` are usable on their own and know nothing about
Kompot components: one manages form state, one assigns variants, one walks a graph of steps. They
keep their names for that reason.

The realtime pair is worth separating in your head from `kompot-realtime`, which is only the frame
contract. `kompot-realtime-server` delivers to the subscribers of one process and defines the bus
between processes; its default bus is in-memory, so a single-instance application needs no
infrastructure. `kompot-realtime-redis` is the bus for the multi-instance case, and it is pub/sub
without delivery guarantees on purpose: a component update is a thing you can afford to lose, since
the client gets current state with its next screen request anyway.

### 🔌 Installation

```kotlin
repositories {
    maven("https://reposilite.kotlin.website/snapshots")
}

dependencies {
    implementation("io.github.youndie:kompot-core:0.1.0")
    implementation("io.github.youndie:kompot-standard:0.1.0")
    implementation("io.github.youndie:kompot-ktor:0.1.0")
}
```

To have registrations generated, add KSP to the module that declares components and give it a tag
unique to that module:

```kotlin
plugins { id("com.google.devtools.ksp") }

dependencies {
    implementation("io.github.youndie:kompot-registry-annotations:0.1.0")
    ksp("io.github.youndie:kompot-registry-processor:0.1.0")
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

- **it does not ship renderers** — the toolkit defines contracts and generates registrations; the
  Compose or SwiftUI side is the application's;
- **it does not choose a transport** — the live-update contract is here, the SSE or WebSocket
  implementation is yours;
- **it does not validate your business rules** — `form-core` covers what a form can decide locally;
  limits and balances belong to the server, and the client only highlights the field it names;
- **it does not assemble your component set for you** — the application composes its own
  `SerializersModule` from the generated pieces, which is precisely why the library never has to
  know what your product contains.

### 🛠️ Building

```bash
./gradlew build
```

Java 25 for every module at once — not tidiness but a Gradle requirement: it tags variants with the
`org.gradle.jvm.version` attribute and refuses to build a module on 21 against a dependency on 25.

### 📄 License

MIT.
