package io.github.youndie.kompot

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.kompot.standard.SequenceAction

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

    // The update channel failed: the screen keeps what it has and stops changing. The same survival
    // as a hole in the vocabulary and the same silence without a report — a live screen that has
    // quietly become a picture of itself looks exactly like one nobody updated.
    //
    // A member of its own rather than a fourth KompotDegradationKind, because the shape is different:
    // there is no wire type to name, and the one thing a reader needs — why the channel went away, a
    // 401 or a dropped connection — is the exception, which `onUnknown` has no place for. Defaulted, so
    // a sink written as a lambda still compiles and reports this the way the toolkit does; a deployment
    // that routes its degradations overrides it to route this too. Never called for cancellation:
    // leaving the screen or changing the topic ends the subscription on purpose.
    public fun onRealtimeFailure(
        // The opaque topic the server handed out (SPEC.md §10.4), exactly as it was subscribed to.
        topic: String,
        cause: Throwable,
    ) {
        println("[Kompot] realtime \"$topic\" failed: $cause")
    }
}

/** The toolkit's default sink: a line on standard output. Named so the paths outside composition — the answers `withPerform` and `withLoginSubmit` feed back — can default to the same thing. */
public val KompotPrintingDegradationSink: KompotDegradationSink =
    KompotDegradationSink { kind, originalType, outcome ->
        println("[Kompot] $kind \"$originalType\" ${outcome.name.lowercase()}")
    }

public val LocalKompotDegradationSink: ProvidableCompositionLocal<KompotDegradationSink> =
    staticCompositionLocalOf { KompotPrintingDegradationSink }

// What a client cannot understand in an action, reported: the action itself, and every part of a
// sequence (SPEC.md §16.4). One rule for the two roads an action arrives by — raised by a node on the
// screen (ReportingActionHandler) and answered by the server (withPerform, withLoginSubmit), which
// enters the chain past the renderer's wrapper and used to be reported nowhere (B-60).
internal fun KompotDegradationSink.reportUnknown(action: KompotAction) {
    when (action) {
        is UnknownAction -> onUnknown(KompotDegradationKind.UNKNOWN_ACTION, action.originalType, KompotDegradationOutcome.NOTHING)
        is SequenceAction -> action.actions.forEach { reportUnknown(it) }
        else -> Unit
    }
}

// An action handler that reports what it cannot understand before passing it on. Its own type is the
// marker that keeps it from wrapping twice: RenderNode wraps at every level of the tree, and a child
// receives its parent's wrapper, so without this a tap five nodes deep would be reported five times.
internal class ReportingActionHandler(
    val delegate: KompotActionHandler,
    private val sink: KompotDegradationSink,
) : KompotActionHandler {
    // Into sequences too: a part this client does not know is as much a hole as a whole action it does
    // not know, and the rest of the sequence still runs (SPEC.md §16.4) — so the tap did SOMETHING,
    // which makes the missing part easy to miss.
    override fun handle(action: KompotAction) {
        sink.reportUnknown(action)
        delegate.handle(action)
    }
}
