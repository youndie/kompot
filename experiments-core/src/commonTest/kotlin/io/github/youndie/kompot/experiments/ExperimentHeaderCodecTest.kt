package io.github.youndie.kompot.experiments

import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentHeaderCodecTest {
    @Test
    fun `round-trips a single assignment`() {
        val encoded = ExperimentHeaderCodec.encode(mapOf("home_banner_copy" to "variant_b"))
        assertEquals(mapOf("home_banner_copy" to "variant_b"), ExperimentHeaderCodec.decode(encoded))
    }

    @Test
    fun `round-trips multiple assignments`() {
        val assignments = mapOf("home_banner_copy" to "variant_b", "checkout_flow" to "control")
        assertEquals(assignments, ExperimentHeaderCodec.decode(ExperimentHeaderCodec.encode(assignments)))
    }

    @Test
    fun `decoding null or blank header returns empty map`() {
        assertEquals(emptyMap(), ExperimentHeaderCodec.decode(null))
        assertEquals(emptyMap(), ExperimentHeaderCodec.decode(""))
    }

    @Test
    fun `decoding malformed entries skips them instead of throwing`() {
        assertEquals(mapOf("valid" to "variant"), ExperimentHeaderCodec.decode("valid=variant,malformed_no_equals"))
    }

    @Test
    fun `encoding empty map produces empty string`() {
        assertEquals("", ExperimentHeaderCodec.encode(emptyMap()))
    }
}
