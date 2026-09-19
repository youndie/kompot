package io.github.youndie.kompot

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

// What a client meets that it does not know. Degradation turns a crash into a hole, and that is the
// point of it — but a crash is reported by every crash reporter ever written and a hole is reported
// by nobody, so the mechanism that lets an old client survive a new server also makes the survival
// unobservable. "How many installs are missing this component" is the question a staged rollout is
// decided on, and until now the only answer was a println: not a logcat tag anyone filters, nothing
// at all on iOS, and unroutable to a deployment's own logging in either case.
public enum class KompotDegradationKind {
    // The response carried a component type the serializers module does not know, so it decoded to
    // UnknownComponent (SPEC.md §2.1).
    UNKNOWN_COMPONENT,

    // The type decoded, and this client's registry has no renderer for it. A different hole with the
    // same consequence: a build that knows the protocol but was assembled without a plug-in.
    UNRENDERABLE_COMPONENT,

    // An action the serializers module does not know, delivered to the handler as UnknownAction —
    // a tap that reaches the application and can do nothing.
    UNKNOWN_ACTION,
}

// WHAT THE PERSON IN FRONT OF THE SCREEN ACTUALLY SAW. Three outcomes, because the two that used to
// share a `true` are not the same event to anybody reading a log: a placeholder this build drew
// because it lacks a renderer, and a component the SERVER chose as a stand-in.
//
// It replaced a boolean named drawnAsFallback, which answered "was anything drawn" and was read as
// "was the server's fallback drawn". The default sink printed "drawn through its fallback" for a
// missing renderer, where no fallback existed and none had been sent.
public enum class KompotDegradationOutcome {
    // The node is a hole. The screen survived it, which is the point of degrading at all, and nothing
    // stands where the component would have been.
    NOTHING,

    // The toolkit's own placeholder: this build understands the type and has no renderer for it, so
    // it says so on the screen rather than leaving a gap.
    PLACEHOLDER,

    // The equivalent the server named for a type it knew a client might not have (SPEC.md §2.1). The
    // only outcome of the three that somebody chose deliberately.
    SERVER_FALLBACK,
}

// Defaulted to what the toolkit did before, so nothing changes for a deployment that does not set
// one; a deployment that does gets its breadcrumbs, its crash context and its counters.
public fun interface KompotDegradationSink {
    public fun onUnknown(
        kind: KompotDegradationKind,
        // The WIRE name, in every kind: it is the string a server writes, a schema declares and a
        // person greps for. A Kotlin class name here would leave one of the two kinds unsearchable by
        // the only name its reader has.
        originalType: String,
        outcome: KompotDegradationOutcome,
    )
}

public val LocalKompotDegradationSink: ProvidableCompositionLocal<KompotDegradationSink> =
    staticCompositionLocalOf {
        KompotDegradationSink { kind, originalType, outcome ->
            println("[Kompot] $kind \"$originalType\" ${outcome.name.lowercase()}")
        }
    }

// An action handler that reports what it cannot understand before passing it on. Its own type is the
// marker that keeps it from wrapping twice: RenderNode wraps at every level of the tree, and a child
// receives its parent's wrapper, so without this a tap five nodes deep would be reported five times.
internal class ReportingActionHandler(
    val delegate: KompotActionHandler,
    private val sink: KompotDegradationSink,
) : KompotActionHandler {
    override fun handle(action: KompotAction) {
        if (action is UnknownAction) {
            sink.onUnknown(KompotDegradationKind.UNKNOWN_ACTION, action.originalType, KompotDegradationOutcome.NOTHING)
        }
        delegate.handle(action)
    }
}
