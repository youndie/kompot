package io.github.youndie.kompot.realtime.redis

import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandExecutionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// What happens when the server REFUSES the one PSUBSCRIBE the bus sends (issue #206): NOAUTH on a
// password-protected Redis reached without one, an ACL user without the right to subscribe, a proxy
// that does not pass pub/sub. Unlike RedisKompotUpdateBusTest, a fake server is the right tool here:
// what is under test is how this code reacts to an error reply, not a property of Redis — and a fake
// runs in every build, without REDIS_URL.
//
// Before the fix the subscription's future was dropped: messages() stayed open, emitted nothing and
// never failed, and every one of these tests ran into its timeout.
class RefusedSubscriptionTest {
    private val servers = mutableListOf<RefusingServer>()
    private val clients = mutableListOf<RedisClient>()
    private val scopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun tearDown() {
        scopes.forEach { it.cancel() }
        clients.forEach { it.shutdown() }
        servers.forEach { it.close() }
    }

    private fun refusingBus(): Pair<RefusingServer, RedisKompotUpdateBus> {
        val server = RefusingServer().also { servers += it }
        val client = RedisClient.create("redis://127.0.0.1:${server.port}").also { clients += it }
        return server to RedisKompotUpdateBus(client, channelPrefix = "test:${System.nanoTime()}")
    }

    @Test
    fun `a refused PSUBSCRIBE fails messages with the server error`() {
        val (_, bus) = refusingBus()
        runBlocking {
            val failure =
                assertFailsWith<RedisCommandExecutionException> {
                    withTimeout(5.seconds) { bus.messages().collect { } }
                }
            assertContains(failure.message.orEmpty(), REFUSAL)
        }
    }

    // The flow fails before it reaches awaitClose, so its cleanup must not live only there: the
    // subscriber connection would otherwise stay open after the failure, one per retry.
    @Test
    fun `a refused subscription closes its connection`() {
        val (server, bus) = refusingBus()
        runBlocking {
            assertFailsWith<RedisCommandExecutionException> {
                withTimeout(5.seconds) { bus.messages().collect { } }
            }
            withTimeout(5.seconds) {
                while (server.openConnections.get() != 0) delay(20.milliseconds)
            }
            assertEquals(0, server.openConnections.get())
        }
    }

    // One level up: the broadcaster's bus collector is a coroutine launched in the application's
    // scope. The refusal must reach that scope — its exception handler, or its parent job — rather
    // than end in a broadcaster that silently delivers nothing.
    @Test
    fun `a refusal reaches the scope a broadcaster was started in`() {
        val (_, bus) = refusingBus()
        runBlocking {
            val reported = CompletableDeferred<Throwable>()
            val scope =
                CoroutineScope(
                    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> reported.complete(e) },
                ).also { scopes += it }

            val collector = KompotUpdateBroadcaster(bus).start(scope)

            val failure = withTimeout(5.seconds) { reported.await() }
            assertIs<RedisCommandExecutionException>(failure)
            assertContains(failure.message.orEmpty(), REFUSAL)
            collector.join()
            assertTrue(collector.isCancelled, "the job start() returned is still running after the refusal")
        }
    }
}

private const val REFUSAL = "ERR control: PSUBSCRIBE refused"

// Just enough of a RESP server for Lettuce: HELLO is refused, so the client falls back to RESP2;
// PSUBSCRIBE is refused with REFUSAL; anything else — CLIENT SETINFO and the like — is OK. It counts
// the connections a client still holds open.
private class RefusingServer : AutoCloseable {
    private val socket = ServerSocket(0)
    val port: Int = socket.localPort
    val openConnections = AtomicInteger()

    init {
        thread(isDaemon = true, name = "refusing-resp-server") {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: break
                openConnections.incrementAndGet()
                thread(isDaemon = true) {
                    try {
                        serve(connection)
                    } finally {
                        openConnections.decrementAndGet()
                    }
                }
            }
        }
    }

    private fun serve(connection: Socket) =
        connection.use {
            val input = it.getInputStream().buffered()
            val output = it.getOutputStream()
            while (true) {
                val command = input.readCommand() ?: break
                val reply =
                    when (command.firstOrNull()?.uppercase()) {
                        "HELLO" -> "-NOPROTO unsupported protocol version\r\n"
                        "PSUBSCRIBE" -> "-$REFUSAL\r\n"
                        else -> "+OK\r\n"
                    }
                output.write(reply.encodeToByteArray())
                output.flush()
            }
        }

    override fun close() = socket.close()
}

// A command as a client sends it: an array of bulk strings. Null once the client has gone.
private fun InputStream.readCommand(): List<String>? {
    val header = readLineOrNull() ?: return null
    check(header.startsWith("*")) { "not a RESP array: $header" }
    return List(header.drop(1).toInt()) {
        val length = checkNotNull(readLineOrNull()) { "connection closed inside a command" }.drop(1).toInt()
        readNBytes(length).decodeToString().also { readNBytes(2) }
    }
}

private fun InputStream.readLineOrNull(): String? {
    val line = StringBuilder()
    while (true) {
        when (val byte = read()) {
            -1 -> return null
            '\r'.code -> return line.toString().also { read() }
            else -> line.append(byte.toChar())
        }
    }
}
