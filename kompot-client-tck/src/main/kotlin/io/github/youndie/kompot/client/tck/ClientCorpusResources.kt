package io.github.youndie.kompot.client.tck

// The corpus that travels inside this artefact. A consumer should not have to know a path, a file
// layout or how many cases there are — the same reason KompotSpecResources exists for the schemas.
//
// Reading it from the classpath rather than from a directory is what makes a vendored copy
// unnecessary, and a vendored copy is how a corpus stops matching the specification it came from.
public object ClientCorpusResources {
    private const val INDEX = "index.json"

    public fun cases(): List<ClientCase> = ClientCorpusRunner.casesFrom(index = read(INDEX), read = ::read)

    private fun read(name: String): String =
        ClientCorpusResources::class.java.classLoader
            ?.getResourceAsStream(name)
            ?.bufferedReader()
            ?.readText()
            ?: error("$name is missing from kompot-client-tck — the corpus did not travel with the artefact")
}
