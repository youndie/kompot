package io.github.youndie.kompot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class TestAction(
    val tag: String,
) : KompotAction

class ActionInterceptorTest {
    @Test
    fun `interceptors run in order when each calls proceed`() {
        val calls = mutableListOf<String>()
        val handler =
            kompotActionHandler(
                listOf(
                    KompotActionInterceptor { chain ->
                        calls += "first"
                        chain.proceed()
                    },
                    KompotActionInterceptor { chain ->
                        calls += "second"
                        chain.proceed()
                    },
                    KompotActionInterceptor { chain ->
                        calls += "third"
                        chain.proceed()
                    },
                ),
            )

        handler.handle(TestAction("tap"))

        assertEquals(listOf("first", "second", "third"), calls)
    }

    @Test
    fun `interceptor that does not call proceed stops the chain`() {
        val calls = mutableListOf<String>()
        val handler =
            kompotActionHandler(
                listOf(
                    KompotActionInterceptor { chain ->
                        calls += "analytics"
                        chain.proceed()
                    },
                        KompotActionInterceptor { calls += "permission-denied" }, // never calls proceed()
                    KompotActionInterceptor { calls += "navigation" },
                ),
            )

        handler.handle(TestAction("tap"))

        assertEquals(listOf("analytics", "permission-denied"), calls)
        assertFalse("navigation" in calls)
    }

    @Test
    fun `empty interceptor list does not throw`() {
        val handler = kompotActionHandler(emptyList())

        handler.handle(TestAction("tap"))

            assertTrue(true) // reaching this line is the assertion: nothing blew up
    }

    @Test
    fun `interceptor can substitute the action for the rest of the chain`() {
        var seenByLast: KompotAction? = null
        val handler =
            kompotActionHandler(
                listOf(
                    KompotActionInterceptor { chain -> chain.proceed(TestAction("enriched")) },
                    KompotActionInterceptor { chain -> seenByLast = chain.action },
                ),
            )

        handler.handle(TestAction("original"))

        assertEquals(TestAction("enriched"), seenByLast)
    }
}
