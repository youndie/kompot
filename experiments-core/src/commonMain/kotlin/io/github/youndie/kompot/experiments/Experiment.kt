package io.github.youndie.kompot.experiments

// One experiment variant. weight is relative, not a percentage: weights are normalised by their
// sum across all variants. By default every variant is equally likely.
public data class Variant(
    val id: String,
    val weight: Int = 1,
)

// An A/B experiment: the set of variants ExperimentAssigner deterministically distributes subjects
// across. Which experiments exist, and what to show for each variant, stays with the application —
// this module knows only the distribution mechanism.
public data class Experiment(
    val id: String,
    val variants: List<Variant>,
) {
    init {
        require(variants.isNotEmpty()) { "Experiment '$id' must have at least one variant" }
        require(variants.all { it.weight > 0 }) { "Experiment '$id' variant weights must be positive" }
    }

    val totalWeight: Int = variants.sumOf { it.weight }
}
