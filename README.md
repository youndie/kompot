# kompot

[![kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![maven central](https://img.shields.io/maven-central/v/io.github.youndie.kompot/kompot-core?label=kompot&color=40c14a)](https://central.sonatype.com/namespace/io.github.youndie.kompot)
[![what a consumer gets](https://raw.githubusercontent.com/youndie/kompot/badges/io.github.youndie.kompot.kompot-core.svg)](https://github.com/youndie/proba)

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
  writes its polymorphic registration and renderer entry;
- **open string tokens** — a backend may send `ColorToken("promo_gold")` with no client release; a
  server-driven theme (`kompot-theme`) can also say what to paint it with;
- **forms that stay client-side** — validation, visibility conditions and cross-field rules run
  locally, and only a server-relevant change asks the backend for a patch;
- **live updates** — a screen can name its own update channel, per user, across several server
  instances;
- **multi-step flows as pure functions** — a wizard graph is `(session, transition, draft) -> session`,
  so branching is covered by unit tests with no HTTP, no database and no UI in sight;
- **it degrades rather than breaks** — an unknown component, an unknown action or a malformed theme
  token costs a widget or some styling, never the screen.

[**youndie.github.io/kompot**](https://youndie.github.io/kompot/) shows the last point live: a body
on the left, the screen on the right, and a switch to "a client released earlier" — a component it
has never heard of leaves a hole while the rest of the screen stays.

### 🔌 Installation

Every module is published to Maven Central under one version; take it once, from the platform, and
name no version anywhere else. The badge above shows the latest one.

```kotlin
dependencies {
    implementation(platform("io.github.youndie.kompot:kompot-bom:0.38.0"))

    implementation("io.github.youndie.kompot:kompot-core")
    implementation("io.github.youndie.kompot:kompot-standard")
    implementation("io.github.youndie.kompot:kompot-ktor")
}
```

Use the platform rather than repeating the version: it constrains every coordinate this build
publishes, the per-target ones (`kompot-core-jvm`, `kompot-core-iosarm64`, …) included, so no
combination nobody built can be written down. Snapshots of `main`, `<version>.<ci run>`, are in
`https://reposilite.kotlin.website/snapshots`.

To have registrations generated, apply KSP to the module that declares components — the setup, and
why a component and its renderer belong in different modules, are in
[`kompot-registry-processor`](kompot-registry-processor/README.md). A consumer of that module never
applies KSP.

Moving up a version and meeting a compile error: [UPGRADING.md](UPGRADING.md) says what to write
instead. kompot is `0.x` and promises no stability — only that a break it declared is on that page.

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

The server builds a tree and responds with it:

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

Through `respondKompotComponent`, not `call.respond`: the latter resolves the serialiser from the
runtime class and drops the `"type"` discriminator on the ROOT of the tree, so the client receives an
unknown component while every nested child looks fine.

To see the screen without running a client, [`kompot-preview`](kompot-preview/README.md) draws the
body an endpoint returns in an IDE `@Preview`, and [`kompot-studio`](kompot-studio/README.md) opens
a build's recorded bodies in an editor with the checks a body has to pass.

![kompot studio](docs/images/kompot-studio.png)

### 📦 Modules

| module | what for | depends on |
| --- | --- | --- |
| `kompot-core` | the tree, actions, modifiers, design-system tokens | — |
| `kompot-bom` | the platform: one version for every coordinate this build publishes | — |
| `kompot-registry-annotations` | the `@KompotComponentMarker` annotation | — |
| [`kompot-registry-processor`](kompot-registry-processor/README.md) | the KSP processor that generates registrations | — |
| `kompot-standard` | the standard component set: text, containers, lists, tabs, pagination | core |
| `kompot-forms` | form components over `form-core` | core, form-core |
| `kompot-theme` | server-driven theming, no UI toolkit | core |
| `kompot-ds-material` | the reference Material3 token set: constants a server and a client share | core |
| `kompot-ktor` | Ktor helpers for polymorphic roots, ETags, experiment headers | core, experiments-core |
| `kompot-realtime` | the live-update frame contract | core |
| `kompot-realtime-server` | delivery to one instance's subscribers, with an in-memory bus between instances | kompot-realtime |
| `kompot-realtime-redis` | a Redis pub/sub bus for more than one instance — no delivery guarantee, the next screen request catches up | kompot-realtime-server |
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
| [`kompot-spec`](kompot-spec/README.md) | the wire specification: schema generator, validator, compatibility check | all of them |
| [`kompot-tck`](kompot-tck/README.md) | the conformance kit: walks a running server over HTTP and checks the rules a schema cannot express | kompot-spec |
| [`kompot-client-tck`](kompot-client-tck/README.md) | the case corpus that holds a CLIENT to the rules only it carries | — |
| `kompot-swift-interop` | what the Kotlin/Native ObjC export drops — reified calls, suspend contracts, value-class tokens | core, standard, forms, wizard |
| [`kompot-client`](kompot-client/README.md) | the Compose client: a registry keyed by wire type, the core renderers, live updates, impression tracking | core, standard, forms |
| `kompot-forms-client`, `kompot-wizard-client`, `kompot-images-client-coil` | the renderers of the form, wizard and image plug-ins | kompot-client |
| `kompot-theme-client`, `kompot-ds-material-compose` | the Compose side of a server-driven theme, and the Material3 design system | kompot-client, kompot-theme |
| [`kompot-preview`](kompot-preview/README.md) | a response body drawn by the real renderers, so a server can see the screen it built | kompot-client, kompot-forms |
| [`kompot-studio`](kompot-studio/README.md), `kompot-studio-gradle-plugin` | the desktop editor for a body, and the Gradle task that opens it on a consumer's build | kompot-preview, kompot-spec |
| `kompot-client-cache` | offline-first screen cache with ETag revalidation | core |
| `kompot-analytics` | tracking contracts for screens, actions and form outcomes | — |

`form-core`, `experiments-core` and `wizard-core` know nothing about Kompot components and are
usable on their own.

### 📐 The wire, from another stack

The protocol is written for somebody implementing a server — or a client — in another language:

- [`kompot-spec/SPEC.md`](kompot-spec/SPEC.md) — the rules, including the ones a schema cannot
  express: degradation, extensibility, forms, pagination, transport, and which changes the other end
  survives (§15);
- [`kompot-spec/schema`](kompot-spec/schema) — a JSON Schema per protocol module, generated from the
  same descriptors that encode a response;
- [`kompot-tck`](kompot-tck/README.md) and [`kompot-client-tck`](kompot-client-tck/README.md) — the
  conformance kits for a server and for a client.

Every snapshot of `main` is also read back from outside by [proba](https://github.com/youndie/proba),
which resolves the published coordinates the way a stranger's build does; the third badge is its
verdict on `kompot-core`, and `1 unchecked` is not `clean`.

### 🚫 What it does not do

- **it does not ship YOUR renderers** — the standard, form, wizard and image renderers and a
  Material3 design system are here; a component you invented needs a renderer you write. SwiftUI
  rendering is yours too;
- **it does not choose a transport** — the live-update contract is here, SSE or WebSocket is yours;
- **it does not validate your business rules** — `form-core` covers what a form can decide locally;
  limits and balances belong to the server;
- **it does not assemble your component set for you** — the application composes its own
  `SerializersModule` from the generated pieces.

### 🎯 Targets

Every protocol module publishes for **JVM, Android, the three iOS targets and `wasmJs`**, and every
Compose module for the same set minus **`iosX64`**, which Compose no longer publishes for. The
modules are common code with no `expect`/`actual`, so the same screens draw on a desktop, a phone,
an iPhone and in a browser.

The Compose half is built against **Compose Multiplatform 1.12.1** and **material3
1.12.0-alpha03**; a consumer on another Compose line compiles and then fails at the first screen, so
stay on the same one. On Android every module asks for `compileSdk` 37.

Three modules are JVM-only on purpose: `kompot-ktor` and `kompot-realtime-server` (server helpers)
and `kompot-forms-standard` (a DSL that builds a form on the server).

### 🧭 Laying out a screen

[`skills/kompot-layout/SKILL.md`](skills/kompot-layout/SKILL.md) is the method for taking a screen
from a design canvas to the wire, written as a Claude Code skill; [`tools/canvas`](tools/canvas/README.md)
are its measuring instruments.

### 🛠️ Building

```bash
./gradlew build
```

Java 17, as a toolchain. On a Mac without an iOS simulator runtime the simulator test tasks fail —
install one through Xcode, or skip them:

```bash
./gradlew build -x iosSimulatorArm64Test -x iosX64Test
```

### 📄 License

MIT.
