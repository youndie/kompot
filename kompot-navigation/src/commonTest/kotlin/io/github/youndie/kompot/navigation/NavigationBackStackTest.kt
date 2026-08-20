package io.github.youndie.kompot.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationBackStackTest {
    @Test
    fun `a fresh stack has one entry, is that entry's current, and cannot go back`() {
        val stack = NavigationBackStack("app://promo")

        assertEquals("app://promo", stack.current)
        assertFalse(stack.canGoBack)
    }

    @Test
    fun `push adds a new entry and becomes current, back is now possible`() {
        val stack = NavigationBackStack("app://promo").push("app://catalogue/item")

        assertEquals("app://catalogue/item", stack.current)
        assertTrue(stack.canGoBack)
        assertEquals(listOf("app://promo", "app://catalogue/item"), stack.entries)
    }

    @Test
    fun `pushing the deeplink already on top is a no-op, not a duplicate`() {
        val stack = NavigationBackStack("app://promo").push("app://promo")

        assertEquals(listOf("app://promo"), stack.entries)
        assertFalse(stack.canGoBack)
    }

    @Test
    fun `pop returns to the previous entry`() {
        val stack = NavigationBackStack("app://promo").push("app://catalogue/item").pop()

        assertEquals("app://promo", stack.current)
        assertFalse(stack.canGoBack)
    }

    @Test
    fun `pop on a single-entry stack is a no-op`() {
        val stack = NavigationBackStack("app://promo").pop()

        assertEquals("app://promo", stack.current)
        assertFalse(stack.canGoBack)
    }

    @Test
    fun `push then pop through three entries returns each previous route in order`() {
        var stack = NavigationBackStack("app://promo").push("app://catalogue/item").push("app://third")
        assertEquals("app://third", stack.current)

        stack = stack.pop()
        assertEquals("app://catalogue/item", stack.current)
        assertTrue(stack.canGoBack)

        stack = stack.pop()
        assertEquals("app://promo", stack.current)
        assertFalse(stack.canGoBack)
    }
}
