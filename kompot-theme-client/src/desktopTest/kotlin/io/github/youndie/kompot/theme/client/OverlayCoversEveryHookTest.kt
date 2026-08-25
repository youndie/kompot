package io.github.youndie.kompot.theme.client

import io.github.youndie.kompot.KompotDesignSystem
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The bug this file exists for was not "resolveSurface was wrong" but "a hook was added and the
// overlay did not notice". The hook carried a default, so nothing failed to compile, nothing failed
// to run, and a theme silently answered for a question it knows nothing about.
//
// So the property is stated once, over whatever the interface holds today: an overlay must answer
// every hook itself. A fourth one added tomorrow fails here rather than in somebody's deployment
// after their theme arrives.
class OverlayCoversEveryHookTest {
    @Test
    fun `the overlay declares an override for every hook the design system has`() {
        // Kotlin reflection rather than Java's: a class that does NOT override an interface default
        // still carries a synthetic Java method for it, so declaredMethods reports every hook as
        // answered and the guard passes over exactly the state it exists to catch. declaredMemberFunctions
        // reports what the class itself declares.
        val hooks = KompotDesignSystem::class.declaredMemberFunctions.map { it.name }.toSet()
        val overridden = RemoteThemeDesignSystem::class.declaredMemberFunctions.map { it.name }.toSet()

        assertTrue(hooks.isNotEmpty(), "no hooks were found on the interface — this test proved nothing")
        assertEquals(
            emptySet(),
            hooks - overridden,
            "RemoteThemeDesignSystem answers the interface default for these, so a theme replaces what " +
                "the design system it wraps would have said",
        )
    }
}
