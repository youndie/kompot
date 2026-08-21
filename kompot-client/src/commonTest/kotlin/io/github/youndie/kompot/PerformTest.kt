package io.github.youndie.kompot

import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.standard.NavigateAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class PerformTestResultAction(
    val screen: String,
) : KompotAction

@OptIn(ExperimentalCoroutinesApi::class)
class PerformTest {
    @Test
    fun `performing sends the action's own payload and feeds the result back into the chain`() =
        runTest {
            var sentTo: String? = null
            var sentPayload: Map<String, FieldValue>? = null
            var forwarded = mutableListOf<KompotAction>()
            val handler =
                KompotActionHandler { forwarded += it }
                    .withPerform(this) { url, payload ->
                        sentTo = url
                        sentPayload = payload
                        PerformTestResultAction("/board")
                    }

            handler.handle(PerformAction(url = "/tasks/move", payload = mapOf("taskId" to TestValue("T-42"))))
            advanceUntilIdle()

            assertEquals("/tasks/move", sentTo)
            assertEquals(mapOf("taskId" to TestValue("T-42")), sentPayload)
            assertEquals(PerformTestResultAction("/board"), forwarded.last())
        }

    // The reason the action exists: two buttons of one list differ only in what they carry, and the
    // handler must not collapse them. A handler keyed on anything but the payload would pass this
    // test with one call and fail it with two.
    @Test
    fun `two buttons of one list send two different payloads to one address`() =
        runTest {
            val sent = mutableListOf<Pair<String, Map<String, FieldValue>>>()
            val handler = KompotActionHandler {}.withPerform(this) { url, payload -> sent += url to payload; PerformTestResultAction("/board") }

            handler.handle(PerformAction(url = "/tasks/move", payload = mapOf("taskId" to TestValue("T-1"))))
            handler.handle(PerformAction(url = "/tasks/move", payload = mapOf("taskId" to TestValue("T-2"))))
            advanceUntilIdle()

            assertEquals(
                listOf<Pair<String, Map<String, FieldValue>>>(
                    "/tasks/move" to mapOf("taskId" to TestValue("T-1")),
                    "/tasks/move" to mapOf("taskId" to TestValue("T-2")),
                ),
                sent,
            )
        }

    @Test
    fun `the original action is forwarded synchronously, before the send resolves`() =
        runTest {
            var forwarded: KompotAction? = null
            val action = PerformAction(url = "/tasks/move", payload = mapOf("taskId" to TestValue("T-42")))
            val handler = KompotActionHandler { forwarded = it }.withPerform(this) { _, _ -> PerformTestResultAction("/board") }

            handler.handle(action)

                // Only the original has arrived: an analytics wrapper further along the chain sees the
                // press itself, not just its outcome.
            assertEquals(action, forwarded)
        }

    @Test
    fun `an action with no payload still reaches the transport`() =
        runTest {
            var called = false
            val handler = KompotActionHandler {}.withPerform(this) { _, payload -> called = true; assertEquals(emptyMap<String, FieldValue>(), payload); PerformTestResultAction("/x") }

            handler.handle(PerformAction(url = "/session/refresh"))
            advanceUntilIdle()

            assertEquals(true, called)
        }

    @Test
    fun `other actions pass through untouched`() =
        runTest {
            var sendCalled = false
            var forwarded: KompotAction? = null
            val handler =
                KompotActionHandler { forwarded = it }
                    .withPerform(this) { _, _ -> sendCalled = true; PerformTestResultAction("/x") }

            handler.handle(NavigateAction(deeplink = "/home"))
            advanceUntilIdle()

            assertEquals(false, sendCalled)
            assertEquals(NavigateAction(deeplink = "/home"), forwarded)
        }
}
