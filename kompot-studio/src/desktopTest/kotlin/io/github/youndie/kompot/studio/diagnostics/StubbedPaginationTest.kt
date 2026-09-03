package io.github.youndie.kompot.studio.diagnostics

import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.spec.paginatingTypes
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.toolkitRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The hazard a consumer takes on the moment it makes a paginated screen openable, found by the pilot
// on the first real one: the preview refuses a list without a page loader for a good reason, the
// studio has to open it anyway, and the Capture button then turns the stub into a file.
class StubbedPaginationTest {
    private val list =
        """{"type":"paginated_list","id":"feed","initialItems":[{"type":"text","id":"r","text":"one"}]}"""

    private val plain = """{"type":"column","id":"root","children":[{"type":"text","id":"t","text":"hi"}]}"""

    private val withStub =
        KompotStudioConfig(registry = toolkitRegistry, pageLoader = EmptyPages)

    private val withoutStub = KompotStudioConfig(registry = toolkitRegistry)

    @Test
    fun `a list drawn with a stub says so, and capturing it asks first`() {
        val finding = diagnose(withStub, list).single { it.layer == "render" }

        assertEquals("$", finding.path)
        assertEquals(Severity.WARNING, finding.severity)
        assertTrue("not a golden" in finding.message)
        assertFalse(capturingIsSafe(withStub, list))
    }

    @Test
    fun `a screen without a list, and a list without a stub, say nothing`() {
        // Two controls. The first stops the rule being "every screen is warned about"; the second is
        // the case the toolkit already handles loudly — without a loader the render refuses, and a
        // second warning about it would be noise on top of an error.
        assertEquals(emptyList(), diagnose(withStub, plain).filter { it.layer == "render" })
        assertEquals(emptyList(), diagnose(withoutStub, list).filter { it.layer == "render" })

        assertTrue(capturingIsSafe(withStub, plain))
        assertTrue(capturingIsSafe(withoutStub, list))
    }

    @Test
    fun `the paginating types are read from the schema rather than named`() {
        val types = paginatingTypes(KompotSpecResources("kompot-spec").schemas())

        // Derived, so that a deployment's own list — which must reuse this action, because it is the
        // only thing a client's renderer knows how to call — is covered without anybody adding it to a
        // list here.
        assertEquals(setOf("paginated_list"), types)
    }

    @Test
    fun `a body that does not parse does not block a capture`() {
        // Half-typed text is the normal state of the editor, and a guard that refused on it would
        // refuse most of the time for a reason that has nothing to do with pagination.
        assertTrue(capturingIsSafe(withStub, """{"type":"paginated_list",", """))
    }

    private object EmptyPages : KompotPageLoader {
        override suspend fun loadPage(
            url: String,
            params: Map<String, String>,
        ): KompotPageResponse = KompotPageResponse(items = emptyList())
    }
}
