package io.github.youndie.kompot.studio

import java.util.ServiceLoader

// HOW A TEAM OPENS THE STUDIO: one command, and the configuration comes from the build it is run in.
//
// A `main` of fifteen lines and a run configuration in an IDE is enough for the person who wrote it
// and not for anybody else — "how do I open the studio" should have the same answer as "how do I run
// the tests". So the gradle plugin runs THIS, and this looks in the classpath for what to open.
//
// ServiceLoader rather than a class name in a string: a provider that stops existing is a build
// failure at the moment somebody renames it, while a Class.forName is a runtime message about a class
// nobody remembers writing. The registration is also somewhere a reader can find it — one file under
// META-INF/services — instead of a convention held in a plugin's head.
public interface KompotStudioConfigProvider {
    public fun studioConfig(): KompotStudioConfig

    // What the window is called, for a build that opens more than one.
    public val title: String get() = "kompot studio"

    // The body shown before anything is selected. A deployment with sources rarely needs it.
    public val body: String? get() = null
}

public object KompotStudioLauncher {
    @JvmStatic
    public fun main(args: Array<String>) {
        val providers = ServiceLoader.load(KompotStudioConfigProvider::class.java).toList()

        // Loudly, and with the thing to write in the message. The alternative — opening a default
        // window on the toolkit's own renderers — would look like the studio working and photograph a
        // product nobody ships.
        val provider =
            providers.firstOrNull()
                ?: error(
                    "No KompotStudioConfigProvider on the classpath. Implement " +
                        "io.github.youndie.kompot.studio.KompotStudioConfigProvider, return your " +
                        "KompotStudioConfig from it, and register it in " +
                        "META-INF/services/io.github.youndie.kompot.studio.KompotStudioConfigProvider.",
                )

        if (providers.size > 1) {
            // Not an error: a build with two clients has two, and picking the first is a defensible
            // default. Saying which one was picked is what stops "the studio opened the wrong app"
            // from being a mystery.
            println("kompot studio: ${providers.size} providers found, opening ${provider::class.qualifiedName}")
        }

        val config = provider.studioConfig()
        val body = provider.body
        if (body == null) kompotStudio(config, title = provider.title) else kompotStudio(config, body, provider.title)
    }
}
