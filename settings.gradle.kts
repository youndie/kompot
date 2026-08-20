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

// Server-driven theming: the values a client resolves design-system tokens into. Deliberately free
// of any UI toolkit, because the server that serves a theme has no business depending on Compose.
include(":kompot-theme")

// The Ktor side: helpers that serialise a polymorphic root correctly, which a plain call.respond
// does not.
include(":kompot-ktor")

// The live-update channel contract. The transport is the application's to implement.
include(":kompot-realtime")

// Form state: validation, visibility conditions, cross-field rules and server patches. Usable
// without any of the Kompot components — it knows nothing about them.
include(":form-core")

// Deterministic A/B assignment from (experiment, subject) with no assignment storage, plus the
// header codec that carries the result beside a response.
include(":experiments-core")
