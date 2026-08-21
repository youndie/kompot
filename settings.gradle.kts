rootProject.name = "kompot"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Settings repositories win over module ones. Nothing here declares its own `repositories`
    // block, and nothing should: a module-level block silently overrides this list, and a
    // dependency declared only here then fails to resolve in exactly that module — with an error
    // that reads "artifact not found" while it resolves fine for the module next door.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenCentral()
    }
}

// The protocol core: the component tree, actions, modifiers, design-system tokens. Depends on
// nothing else here.
include(":kompot-core")

// The KSP pair. A module marks its components with @KompotComponentMarker and the processor
// generates that module's polymorphic registration and renderer map, so nothing has to be listed
// by hand. Generated code compiles into the marked module's own artifact, which is why a consumer
// never applies KSP itself.
include(":kompot-registry-annotations")
include(":kompot-registry-processor")

// The standard component set — text, containers, lists, pagination — and the form components on
// top of form-core. Both are plug-ins over :kompot-core rather than part of it: an application may
// take one, the other or neither.
include(":kompot-standard")
include(":kompot-forms")

// An image by URL, as a component plug-in of its own. Fetching and caching are the client's
// business; nothing here knows about an image library.
include(":kompot-images")

// The one action that touches authentication: the server hands the client a new session. It
// describes the wire and nothing else — no token storage, no refresh logic.
include(":kompot-auth")

// Server-driven theming: the values a client resolves design-system tokens into. Deliberately free
// of any UI toolkit, because the server that serves a theme has no business depending on Compose.
include(":kompot-theme")

// The Ktor side: helpers that serialise a polymorphic root correctly, which a plain call.respond
// does not.
include(":kompot-ktor")

// The live-update channel contract. The transport is the application's to implement.
include(":kompot-realtime")

// The server side of that channel: delivery to the subscribers of one instance, plus the bus
// contract between instances. The in-memory bus is the default, so a single-instance application
// needs no infrastructure at all; :kompot-realtime-redis is the multi-instance backend.
include(":kompot-realtime-server")
include(":kompot-realtime-redis")

// Form state: validation, visibility conditions, cross-field rules and server patches. Usable
// without any of the Kompot components — it knows nothing about them.
include(":form-core")

// The standard field set over form-core: text, amount, checkbox, autocomplete, selection, plus the
// rules and conditions that go with them. A plug-in like any other — an application may take it,
// replace it, or add its own field types beside it.
include(":form-standard")

// The glue between the two: one call declares a field and draws the component that fills it, so a
// fieldId cannot drift between the schema and the UI (SPEC.md §9.2 asks for that connectivity; this
// makes breaking it unrepresentable).
include(":kompot-forms-standard")

// Deterministic A/B assignment from (experiment, subject) with no assignment storage, plus the
// header codec that carries the result beside a response.
include(":experiments-core")

// The wire specification: the JSON Schema generator, the validator for the subset it prints, and
// the spec-module definitions of every module above. An application assembles its own spec from
// these plus its own modules — the closed list of types is a property of a build, not of the
// toolkit.
include(":kompot-spec")

// The conformance kit: the portable half of it. It walks a server over HTTP and checks the rules a
// schema cannot express — unique ids, form connectivity, ETag revalidation, terminating pagination,
// the idempotency contract. It is the only level that tests a running server rather than a
// description, and the only one that runs against an implementation on any stack.
include(":kompot-tck")

// The Swift bridge: non-generic wrappers over the reified and suspend calls that the Kotlin/Native
// ObjC export does not publish, plus the unwrapping of value-class tokens that erode at that
// boundary. It builds no Json of its own — which types an application speaks is the application's.
include(":kompot-swift-interop")

// Multi-step flows. wizard-core is the step machine — a pure function of (session, transition,
// draft), with no HTTP, no storage and no idea what a component is — and kompot-wizard is its wire
// side: the step screen, the three transition actions and the resume request.
include(":wizard-core")
include(":kompot-wizard")

// The navigation graph: which deeplink a plain screen answers to and where its tree is fetched
// from. Only for screens that need no client code of their own.
include(":kompot-navigation")
