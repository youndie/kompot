package io.github.youndie.kompot.studio.source

import io.github.youndie.kompot.navigation.NavigationGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// A running server as a source: the screen list is the deployment's own NavigationGraph, and each
// screen is polled conditionally.
//
// java.net.http and not a Ktor client, though the toolkit already depends on one: this module is
// published, and every dependency it names lands in the POM of a tool a consumer adds to their build.
// The JDK's client does conditional GET with headers, which is the entire requirement.
//
// It does not implement KompotScreenFetcher, which was the obvious thing to reach for. That contract
// hands back a DECODED KompotComponent, and the studio's source of truth is the text — a body whose
// root lost its discriminator is precisely what has to survive the trip to the window intact. The
// shape of the conditional request is borrowed; the return type is not.
internal class HttpSourceSession(
    private val source: ScreenSource.Http,
    private val scope: CoroutineScope,
    private val pollInterval: Long,
) : ScreenSourceSession {
    private val client: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _screens = MutableStateFlow(source.endpoint?.let { listOf(ScreenRef(it, it)) }.orEmpty())
    override val screens: StateFlow<List<ScreenRef>> = _screens.asStateFlow()

    private val bodies = mutableMapOf<String, MutableStateFlow<BodyState>>()
    private val jobs = mutableMapOf<String, Job>()

    // The graph is read ONCE. It describes which screens a deployment has, and that changes on deploy
    // rather than while somebody looks at one — polling it would spend a request a second to learn
    // nothing, and the source already spends one on the body.
    private val graph =
        source.graphPath?.let { path ->
            scope.launch {
                val loaded =
                    runCatching {
                        val response = get(path, ifNoneMatch = null)
                        json.decodeFromString(NavigationGraph.serializer(), response.body())
                    }
                loaded.onSuccess { navigation ->
                    _screens.value =
                        navigation.routes.map { route ->
                            ScreenRef(id = route.endpoint, title = route.title ?: route.deeplink, kind = route.kind)
                        }
                }
            }
        }

    override fun body(ref: ScreenRef): StateFlow<BodyState> {
        val state = bodies.getOrPut(ref.id) { MutableStateFlow(BodyState()) }
        jobs.getOrPut(ref.id) { scope.launch { poll(ref, state) } }
        return state.asStateFlow()
    }

    override fun close() {
        graph?.cancel()
        jobs.values.forEach { it.cancel() }
    }

    private suspend fun poll(
        ref: ScreenRef,
        state: MutableStateFlow<BodyState>,
    ) {
        var etag: String? = null

        while (true) {
            val previous = state.value

            runCatching { get(ref.id, ifNoneMatch = etag) }
                .onSuccess { response ->
                    state.value =
                        when (response.statusCode()) {
                            // The whole reason this source counts checks separately: nothing was sent
                            // back but a header, and the window has to be able to say so.
                            NOT_MODIFIED -> previous.copy(error = null, checks = previous.checks + 1)

                            OK -> {
                                etag = response.headers().firstValue("ETag").orElse(null)
                                val text = response.body()
                                previous.copy(
                                    text = text,
                                    error = null,
                                    checks = previous.checks + 1,
                                    // Compared even on a 200, because a server without an ETag answers
                                    // 200 to every poll: without this the counter would say the screen
                                    // is rewritten once a second, and the number would stop meaning
                                    // anything on exactly the deployments that need it most.
                                    revisions =
                                        if (text == previous.text) {
                                            previous.revisions
                                        } else {
                                            previous.revisions + 1
                                        },
                                )
                            }

                            else ->
                                previous.copy(
                                    error = "${ref.id}: HTTP ${response.statusCode()}",
                                    checks = previous.checks + 1,
                                )
                        }
                }.onFailure { failure ->
                    state.value =
                        previous.copy(
                            error = "${ref.id}: ${failure.message ?: failure::class.simpleName}",
                            checks = previous.checks + 1,
                        )
                }

            delay(pollInterval)
        }
    }

    private suspend fun get(
        path: String,
        ifNoneMatch: String?,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI.create(source.baseUrl.trimEnd('/') + path))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .apply {
                    source.headers.forEach { (name, value) -> header(name, value) }
                    if (ifNoneMatch != null) header("If-None-Match", ifNoneMatch)
                }.GET()
                .build()

        // On IO, and not because the client is blocking by accident: sendAsync would hand back a
        // future this code would then wait on anyway, and a suspending wrapper around a thread pool is
        // the honest shape of it.
        return withContext(Dispatchers.IO) { client.send(request, HttpResponse.BodyHandlers.ofString()) }
    }

    private companion object {
        const val OK = 200
        const val NOT_MODIFIED = 304
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val REQUEST_TIMEOUT_SECONDS = 10L
    }
}
