package io.github.youndie.kompot.studio.source

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The three sources, exercised through their real mechanism: a real file rewritten on disk, a real
// directory gaining a file, a real HTTP server answering 304. None of it is mocked, because every one
// of the defects worth catching here — a rewrite inside the same second, a listing that reshuffles, a
// server that ignores If-None-Match — lives in the mechanism rather than in the code around it.
class ScreenSourceTest {
    @Test
    fun `a file source picks up a rewrite and counts it once`() =
        withScope { scope ->
            val file = tempDir().resolve("home.json")
            file.writeText("""{"type":"text","id":"a","text":"first"}""")

            val session = ScreenSource.File(file).open(scope, pollInterval = POLL)
            val ref = session.screens.value.single()
            val body = session.body(ref)

            val first = body.await { it.text != null }
            assertTrue(first.text!!.contains("first"), "the first read did not return the file: ${first.text}")
            assertEquals(1, first.revisions)

            // Let it poll a few times over an UNCHANGED file first, and assert that those cost nothing.
            // Rewriting straight away would leave the interesting half untested: a counter that ticked
            // once per read would pass that version of this test and be useless for the thing it exists
            // to show, which is telling a rewrite apart from a poll.
            val idle = body.await { it.checks >= 3 }
            assertEquals(
                1,
                idle.revisions,
                "reading the same file ${idle.checks} times counted as ${idle.revisions} revisions",
            )

            file.writeText("""{"type":"text","id":"a","text":"second"}""")

            val second = body.await { it.text?.contains("second") == true }
            assertEquals(2, second.revisions, "a rewrite counted as ${second.revisions} revisions")

            session.close()
        }

    @Test
    fun `a directory source lists what is in it and notices what arrives`() =
        withScope { scope ->
            val dir = tempDir()
            dir.resolve("second.json").writeText("""{"type":"text","id":"b","text":"b"}""")
            dir.resolve("first.json").writeText("""{"type":"text","id":"a","text":"a"}""")
            // Not a recording, and must not be listed: a directory of fixtures also holds READMEs.
            dir.resolve("notes.md").writeText("not a screen")

            val session = ScreenSource.Directory(dir).open(scope, pollInterval = POLL)

            val initial = session.screens.await { it.size == 2 }
            // By name, and asserted: the order a file system hands back entries is not an order, and a
            // list that reshuffles under the cursor is the kind of defect nobody reports.
            assertEquals(listOf("first", "second"), initial.map { it.title })

            dir.resolve("third.json").writeText("""{"type":"text","id":"c","text":"c"}""")

            val grown = session.screens.await { it.size == 3 }
            assertEquals(listOf("first", "second", "third"), grown.map { it.title })

            session.close()
        }

    @Test
    fun `an http source reads the graph and a matching ETag costs no revision`() =
        withScope { scope ->
            val served = ServedScreen(body = """{"type":"text","id":"a","text":"first"}""", etag = "\"v1\"")
            val server = startServer(served)

            try {
                val session =
                    ScreenSource
                        .Http(
                            baseUrl = "http://127.0.0.1:${server.address.port}",
                            graphPath = "/graph",
                        ).open(scope, pollInterval = POLL)

                val routes = session.screens.await { it.isNotEmpty() }
                assertEquals(listOf("/home", "/pay"), routes.map { it.id })
                assertEquals(listOf("Home", "Pay"), routes.map { it.title })
                // The kind travels: a form endpoint answers a different envelope, and the window has to
                // be able to say so before somebody wonders why a screen decodes strangely.
                assertEquals(listOf("screen", "form"), routes.map { it.kind })

                val body = session.body(routes.first())
                val first = body.await { it.text != null }
                assertTrue(first.text!!.contains("first"))
                assertEquals(1, first.revisions)

                // Several polls later: the server has been asked repeatedly and answered 304 every
                // time. This is the assertion the two counters exist for — a source that redownloaded
                // the body would look identical in the window and differ only here.
                val polled = body.await { it.checks >= 4 }
                assertEquals(1, polled.revisions, "revalidation counted a revision it should not have")
                assertTrue(served.notModified.get() > 0, "the server never got a conditional request")

                served.body = """{"type":"text","id":"a","text":"second"}"""
                served.etag = "\"v2\""

                val changed = body.await { it.text?.contains("second") == true }
                assertEquals(2, changed.revisions)

                session.close()
            } finally {
                server.stop(0)
            }
        }

    // A screen the test server serves, and a counter for how often it answered 304. The counter is
    // what stops the ETag assertion from being vacuous: without it, a source that never sent
    // If-None-Match at all would pass every other line above.
    private class ServedScreen(
        @Volatile var body: String,
        @Volatile var etag: String,
    ) {
        val notModified = AtomicInteger()
    }

    private fun startServer(served: ServedScreen): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/graph") { exchange ->
                exchange.respond(
                    200,
                    """
                    {"routes":[
                      {"deeplink":"app://home","endpoint":"/home","title":"Home","kind":"screen"},
                      {"deeplink":"app://pay","endpoint":"/pay","title":"Pay","kind":"form"}
                    ]}
                    """.trimIndent(),
                )
            }
            createContext("/home") { exchange ->
                if (exchange.requestHeaders.getFirst("If-None-Match") == served.etag) {
                    served.notModified.incrementAndGet()
                    exchange.responseHeaders.add("ETag", served.etag)
                    exchange.sendResponseHeaders(304, -1)
                    exchange.close()
                } else {
                    exchange.responseHeaders.add("ETag", served.etag)
                    exchange.respond(200, served.body)
                }
            }
            start()
        }

    private fun HttpExchange.respond(
        code: Int,
        text: String,
    ) {
        val bytes = text.toByteArray()
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun tempDir(): Path = Files.createTempDirectory("kompot-studio-source").also { it.createDirectories() }

    private fun withScope(block: suspend (CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking { block(scope) }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
        withTimeout(AWAIT_TIMEOUT) { first(predicate) }

    private companion object {
        // Far below the second the studio uses, so the suite does not spend its life waiting; the
        // mechanism under test is the same either way.
        const val POLL = 50L
        const val AWAIT_TIMEOUT = 10_000L
    }
}
