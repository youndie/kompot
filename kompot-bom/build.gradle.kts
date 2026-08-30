plugins {
    `java-platform`
    id("ru.workinprogress.sborka.publish")
}

// A version-aligned list of everything this build publishes. The point is not brevity: a kompot
// version carries the CI run number on its tail (0.19.0.41), so any two publishes differ, and
// "kompot-core:0.19.0.41 with kompot-client:0.19.0.42" resolves quietly into a combination nobody
// ever built or tested. A platform makes that combination inexpressible.
//
// The constraints are DERIVED from the publications each module really registers rather than from a
// list kept by hand, and that matters more here than usual: a Kotlin Multiplatform module publishes
// one coordinate per target beside its root one, so a hand-written list would cover a third of what
// exists and look complete.
// evaluationDependsOn, and it is load-bearing rather than defensive. Without it this module is
// configured before some of the others, their publications do not exist yet and their `version` still
// reads "unspecified" — and none of that fails anything: the first attempt produced a perfectly valid
// BOM with 69 of 97 coordinates, several of them pinned to a version by that name.
val published =
    rootProject.subprojects
        .filter { it.path != path }
        .sortedBy { it.path }
        .onEach { evaluationDependsOn(it.path) }

val contributed = mutableMapOf<String, Int>()

dependencies {
    constraints {
        published.forEach { module ->
            val publications =
                module.extensions
                    .findByType(PublishingExtension::class.java)
                    ?.publications
                    ?.withType(MavenPublication::class.java)
                    .orEmpty()

            publications.forEach { publication ->
                api("${publication.groupId}:${publication.artifactId}:${publication.version}")
            }
            contributed[module.path] = publications.size
        }
    }
}

// The failure this guards against does not look like a failure. A module whose publications are not
// visible yet contributes nothing, the build stays green, and the BOM ships covering a fraction of the
// coordinates — worse than covering none, because it looks finished. Dropping evaluationDependsOn
// above takes it from 102 constraints to 19, silently.
//
// The count comes from `published`, which is the list of subprojects and needs no project to have been
// evaluated. An earlier version of this guard asked each module whether it applies the publishing
// convention — and an unevaluated project answers "no", so the guard went blind in exactly the
// situation it exists for and passed a nineteen-coordinate BOM.
val silent = contributed.filterValues { it == 0 }.keys.sorted()
require(contributed.isNotEmpty()) { "the BOM found no module at all — it would ship empty and green" }
require(silent.isEmpty()) {
    "these modules registered no publication when the BOM read them, which means they were read before they were " +
        "configured: $silent"
}
