package io.github.youndie.kompot.studio

import com.jetbrains.JBR
import kotlin.test.Test
import kotlin.test.assertTrue

// Question (1) of the spike, asked where a machine with no screen can answer it.
//
// Jewel's DecoratedWindow does not degrade on a plain JDK — its first statement is
// `if (!JBR.isAvailable()) error(...)` — so the studio's whole window choice hangs on that one call
// returning true on the runtime the build provisions. Whether the decorations then LOOK right needs a
// screen; whether the gate opens does not, and the gate is the part that used to be a guess: the
// obvious `java.vendor` test reads "Oracle Corporation" on a JetBrains Runtime and sent the studio
// down the undecorated path on the very JVM it had just downloaded.
class JetBrainsRuntimeTest {
    @Test
    fun `the runtime this module runs on is the one DecoratedWindow requires`() {
        assertTrue(
            JBR.isAvailable(),
            "the toolchain resolved a JVM that is not a JetBrains Runtime: " +
                "runtime=${System.getProperty("java.runtime.name")} " +
                "vendorVersion=${System.getProperty("java.vendor.version")}",
        )
    }
}
