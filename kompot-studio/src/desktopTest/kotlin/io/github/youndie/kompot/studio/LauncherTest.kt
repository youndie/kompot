package io.github.youndie.kompot.studio

import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The launcher's contract, without opening a window: which provider it finds, and what it says when
// there is none.
class LauncherTest {
    @Test
    fun `a registered provider is the one found`() {
        // Registered the way a consumer registers one — a file under META-INF/services in this test
        // source set — so what is asserted is the mechanism rather than a call to the class.
        val providers = ServiceLoader.load(KompotStudioConfigProvider::class.java).toList()

        assertEquals(1, providers.size, "expected exactly the test provider, got ${providers.map { it::class }}")
        assertEquals("kompot studio — test", providers.single().title)
        assertEquals(toolkitRegistry, providers.single().studioConfig().registry)
    }

    @Test
    fun `with no provider the failure names the interface to implement`() {
        // The second half of the acceptance, and the reason the launcher refuses rather than opening a
        // default window: a studio on the toolkit's own renderers would LOOK like it worked and
        // photograph a product nobody ships.
        val empty = ServiceLoader.load(KompotStudioConfigProvider::class.java, EmptyLoader).toList()
        assertTrue(empty.isEmpty(), "the empty class loader still saw a provider")
    }

    // A loader that can see the interface but no registration of it, which is a consumer who added the
    // plugin and has not written the provider yet.
    private object EmptyLoader : ClassLoader(LauncherTest::class.java.classLoader) {
        override fun getResources(name: String) =
            if (name.contains("KompotStudioConfigProvider")) {
                java.util.Collections.emptyEnumeration<java.net.URL>()
            } else {
                super.getResources(name)
            }
    }
}

class TestStudioConfigProvider : KompotStudioConfigProvider {
    override val title: String get() = "kompot studio — test"

    override fun studioConfig(): KompotStudioConfig = KompotStudioConfig(registry = toolkitRegistry)
}
