package io.github.youndie.kompot.experiments

// FNV-1a 32-bit over the key's UTF-8 bytes, not String.hashCode(). The latter is specified as stable
// on the JVM, but this module deliberately relies on no platform guarantee: it depends on nothing
// platform-specific, so its hash must be portable and verifiable in its own right rather than
// indirectly through a standard-library contract.
private const val FNV_OFFSET_BASIS = 0x811C9DC5.toInt()
private const val FNV_PRIME = 0x01000193

private fun stableHash(key: String): Int {
    var hash = FNV_OFFSET_BASIS
    for (byte in key.encodeToByteArray()) {
        hash = hash xor byte.toInt()
        hash *= FNV_PRIME
    }
    // Mask the sign bit: the modulo below needs a non-negative bucket without special cases.
    return hash and 0x7FFFFFFF
}

// Deterministic distribution of a subject — a user, a device, a session, anything with a stable
// subjectId — across an experiment's variants: the same subjectId ALWAYS lands in the same variant of
// the SAME experiment, with no assignment storage at all. The assignment is computed entirely from
// (experiment.id, subjectId) through a stable hash, so behaviour is a pure function of the data
// rather than mutable state that would have to be synchronised between server instances.
public object ExperimentAssigner {
    public fun assign(
        experiment: Experiment,
        subjectId: String,
    ): String {
        val bucket = stableHash("${experiment.id}:$subjectId").mod(experiment.totalWeight)
        var cumulative = 0
        for (variant in experiment.variants) {
            cumulative += variant.weight
            if (bucket < cumulative) return variant.id
        }
        // Unreachable while totalWeight equals the sum of all weights and bucket is in
        // [0, totalWeight) — but the compiler wants an exhaustive return rather than silent trust in
        // a loop invariant.
        return experiment.variants.last().id
    }
}
