package io.github.youndie.kompot.wizard

import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DslTest {
    @Test
    fun `wizardScreen generates an id when none is given and defaults totalSteps to null and canGoBack to false`() {
        val screen =
            wizardScreen(formId = "checkout", stepId = "details", stepIndex = 0, content = TextComponent(id = "t", text = "hi"))

        assertTrue(screen.id.isNotBlank())
        assertEquals("checkout", screen.formId)
        assertNull(screen.totalSteps)
        assertFalse(screen.canGoBack)
        assertEquals("details", screen.stepId)
        assertEquals(0, screen.stepIndex)
    }

    @Test
    fun `wizardScreen honors an explicit id and the given content`() {
        val content = TextComponent(id = "t", text = "Step 2 of 3")
        val screen =
            wizardScreen(
                formId = "checkout",
                stepId = "approval",
                stepIndex = 1,
                totalSteps = 3,
                canGoBack = true,
                content = content,
                id = "wizard_step_1",
            )

        assertEquals("wizard_step_1", screen.id)
        assertEquals("checkout", screen.formId)
        assertEquals(3, screen.totalSteps)
        assertTrue(screen.canGoBack)
        assertEquals(content, screen.content)
    }
}
