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

// Defaulted to what the toolkit did before, so nothing changes for a deployment that does not set
// one; a deployment that does gets its breadcrumbs, its crash context and its counters.
public fun interface KompotDegradationSink {
    public fun onUnknown(
        kind: KompotDegradationKind,
        originalType: String,
        // Whether anything was drawn in its place. A hole and a placeholder are different facts about
        // a screen, and only the client knows which happened.
        drawnAsFallback: Boolean,
    )
}

public val LocalKompotDegradationSink: ProvidableCompositionLocal<KompotDegradationSink> =
    staticCompositionLocalOf {
        KompotDegradationSink { kind, originalType, drawnAsFallback ->
            println("[Kompot] $kind \"$originalType\"" + if (drawnAsFallback) " drawn through its fallback" else " skipped")
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
            sink.onUnknown(KompotDegradationKind.UNKNOWN_ACTION, action.originalType, drawnAsFallback = false)
        }
        delegate.handle(action)
    }
}
