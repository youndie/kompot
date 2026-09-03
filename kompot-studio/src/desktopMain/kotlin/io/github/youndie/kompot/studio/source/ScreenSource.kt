package io.github.youndie.kompot.studio.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

// WHERE A BODY COMES FROM. Three places, because a deployment already keeps its bodies in three
// places, and the studio's job is to open what exists rather than to ask for a fourth format: a
// recorded response committed beside its test, a directory of such recordings, and a running server
// that answers a NavigationGraph and screens behind it.
//
// A source yields TEXT and not a decoded tree, all the way through. The text is what the client
// receives, what a fixture stores and what a schema is checked against; a source that decoded would
// hide exactly the class of defect this toolkit's preview exists to catch — a body whose root lost
// its discriminator decodes to nothing and reads perfectly.
public sealed interface ScreenSource {
    // What the source is called in the window. Not derived from the path: two directories of
    // recordings called "recorded" are the normal case in a repository with a client and a server.
    public val name: String

    // One recording. The studio watches its file, so the loop "a test rewrites the fixture → the
    // window redraws" needs nobody to press anything.
    public class File(
        public val path: Path,
        override val name: String = path.fileName.toString(),
    ) : ScreenSource

    // A directory of recordings: every *.json in it is a screen, and the list itself follows the
    // directory — a recording added while the window is open appears in it.
    public class Directory(
        public val path: Path,
        override val name: String = path.fileName?.toString() ?: path.toString(),
        public val extension: String = ".json",
    ) : ScreenSource

    // A running server. With `graphPath` the screen list is the deployment's own NavigationGraph —
    // deeplink, endpoint and the kind that says which envelope stands behind it; without one, the
    // single endpoint given.
    public class Http(
        public val baseUrl: String,
        override val name: String = baseUrl,
        public val graphPath: String? = null,
        public val endpoint: String? = null,
        // Bearer tokens and whatever else the deployment's gateway asks for. Sent with every request,
        // including the poll — a token that expires shows up as an error in the window rather than as
        // a screen that quietly stops changing.
        public val headers: Map<String, String> = emptyMap(),
    ) : ScreenSource
}

// One screen a source offers. `id` is what the source addresses it by — a path, an endpoint — and is
// what the studio keys state on; `title` is for reading.
public data class ScreenRef(
    val id: String,
    val title: String,
    // The vocabulary of ScreenRouteKind: screen, form, live_screen. A file source says "screen"
    // because a file cannot know better — the body's own shape decides how it decodes anyway, which is
    // what KompotPreview does with it.
    val kind: String = "screen",
    // The deeplink a graph route answers to, where there is a graph. It is what a navigate action in
    // the log carries, and matching the two is the only way the studio can follow one — a file on
    // disk has no deeplink and gets null, which is why this is not the id.
    val deeplink: String? = null,
)

// What the window knows about one screen's body at a moment.
//
// TWO counters and not one, and the second is the point of the first. `checks` counts how often the
// source was asked; `revisions` counts how often the answer DIFFERED. An ETag exists precisely to make
// those two numbers diverge, and a window that showed only "it reloaded" could not tell a working
// revalidation from a server that ignores If-None-Match — both look like a screen that keeps
// redrawing, and only one of them is fetching the whole body every second.
public data class BodyState(
    val text: String? = null,
    val error: String? = null,
    val checks: Int = 0,
    val revisions: Int = 0,
)

// A source, opened. It lives as long as the scope does: closing cancels the polling.
public interface ScreenSourceSession : AutoCloseable {
    public val screens: StateFlow<List<ScreenRef>>

    // Lazily per ref, and lazily on purpose: the flow is what starts watching that body, so a source
    // offering forty recordings polls the one somebody is looking at.
    public fun body(ref: ScreenRef): StateFlow<BodyState>
}

public fun ScreenSource.open(
    scope: CoroutineScope,
    pollInterval: Long = DEFAULT_POLL_MILLIS,
): ScreenSourceSession =
    when (this) {
        is ScreenSource.File -> FileSourceSession(this, scope, pollInterval)
        is ScreenSource.Directory -> DirectorySourceSession(this, scope, pollInterval)
        is ScreenSource.Http -> HttpSourceSession(this, scope, pollInterval)
    }

// A second, and the same second everywhere.
//
// The research asked for WatchService with SensitivityWatchEventModifier.HIGH, and that turns out to
// buy nothing: on macOS WatchService has no native backend, so HIGH means "poll every two seconds" —
// a slower poll than this one, reached through a com.sun.nio.file API that has been deprecated for
// removal since JDK 15. Worse, it is a DIFFERENT mechanism per platform: on Linux inotify fires
// instantly, so a test of "an edited file reaches the window" would pass in CI for a reason that does
// not exist on the machine the studio is actually used on.
public const val DEFAULT_POLL_MILLIS: Long = 1_000
