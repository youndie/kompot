package io.github.youndie.kompot.studio.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

// The two sources that read from disk. They share their whole mechanism — read, compare with what was
// read last, count a revision only when it differs — which is why they share a file: two pollers that
// drifted apart would answer "did it change?" differently for a directory and for one of its files.

internal class FileSourceSession(
    private val source: ScreenSource.File,
    scope: CoroutineScope,
    private val pollInterval: Long,
) : ScreenSourceSession {
    private val ref = ScreenRef(id = source.path.toAbsolutePath().toString(), title = source.name)

    private val _screens = MutableStateFlow(listOf(ref))
    override val screens: StateFlow<List<ScreenRef>> = _screens.asStateFlow()

    private val state = MutableStateFlow(BodyState())
    private var job: Job? = null
    private val scope = scope

    override fun body(ref: ScreenRef): StateFlow<BodyState> {
        if (job == null) job = scope.launch { pollFile(source.path, state, pollInterval) }
        return state.asStateFlow()
    }

    override fun close() {
        job?.cancel()
    }
}

internal class DirectorySourceSession(
    private val source: ScreenSource.Directory,
    private val scope: CoroutineScope,
    private val pollInterval: Long,
) : ScreenSourceSession {
    private val _screens = MutableStateFlow(listDirectory())
    override val screens: StateFlow<List<ScreenRef>> = _screens.asStateFlow()

    private val bodies = mutableMapOf<String, MutableStateFlow<BodyState>>()
    private val jobs = mutableMapOf<String, Job>()

    private val listing =
        scope.launch {
            while (isActive) {
                delay(pollInterval)
                // Assigned unconditionally: MutableStateFlow drops an equal value, so an unchanged
                // directory costs one list comparison and emits nothing.
                _screens.value = listDirectory()
            }
        }

    override fun body(ref: ScreenRef): StateFlow<BodyState> {
        val state = bodies.getOrPut(ref.id) { MutableStateFlow(BodyState()) }
        jobs.getOrPut(ref.id) { scope.launch { pollFile(Path.of(ref.id), state, pollInterval) } }
        return state.asStateFlow()
    }

    override fun close() {
        listing.cancel()
        jobs.values.forEach { it.cancel() }
    }

    // Sorted by name, because the order a file system hands back its entries is not an order — and a
    // list that reshuffles itself under the cursor while somebody is clicking through recordings is
    // the kind of thing nobody reports and everybody swears at.
    private fun listDirectory(): List<ScreenRef> =
        runCatching {
            Files.list(source.path).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.name.endsWith(source.extension) }
                    .map { path ->
                        ScreenRef(
                            id = path.toAbsolutePath().toString(),
                            title = path.name.removeSuffix(source.extension),
                        )
                    }
                    .toList()
            }
        }.getOrDefault(emptyList()).sortedBy { it.title }
}

// One read, then a read every interval, and a revision counted only when the text is not what it was.
//
// Reading the whole file rather than watching a timestamp: a fixture is kilobytes, and a
// last-modified comparison is exactly the check that misses a rewrite inside the same second — which
// is the normal case when a test writes the file the studio is looking at.
private suspend fun pollFile(
    path: Path,
    state: MutableStateFlow<BodyState>,
    pollInterval: Long,
) {
    while (true) {
        val previous = state.value
        val read = runCatching { Files.readString(path) }

        state.value =
            read.fold(
                onSuccess = { text ->
                    previous.copy(
                        text = text,
                        error = null,
                        checks = previous.checks + 1,
                        revisions = if (text == previous.text) previous.revisions else previous.revisions + 1,
                    )
                },
                onFailure = { failure ->
                    // The text is KEPT. A file being rewritten is briefly unreadable, and blanking the
                    // window every time a test saves one would make the studio unusable against the
                    // very loop it exists for.
                    previous.copy(
                        error = "${path.fileName}: ${failure.message ?: failure::class.simpleName}",
                        checks = previous.checks + 1,
                    )
                },
            )

        delay(pollInterval)
    }
}
