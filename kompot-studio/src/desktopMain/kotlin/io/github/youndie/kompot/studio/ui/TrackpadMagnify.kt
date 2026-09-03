package io.github.youndie.kompot.studio.ui

import java.lang.reflect.Proxy
import javax.swing.JComponent

// THE PINCH. AWT has no idea a trackpad can do that; the JetBrains Runtime does, through Apple's
// eawt gesture API, and the studio runs on that runtime. Reached by reflection, because the
// `com.apple.eawt.event` classes exist only in a macOS JDK and this module is compiled on Linux.
//
// Installed on the root pane: the runtime bubbles a gesture from the component under the cursor up
// to the root, so one listener there hears every pinch in the window, and the caller decides which
// of them are about the preview.
//
// Returns false where there is no such API — a plain OpenJDK, or not macOS — and the window simply
// has no pinch, which is what it had before.
internal fun installMagnification(
    component: JComponent,
    onMagnify: (Double) -> Unit,
): Boolean =
    runCatching {
        val utilities = Class.forName("com.apple.eawt.event.GestureUtilities")
        val listenerType = Class.forName("com.apple.eawt.event.MagnificationListener")
        val gestureType = Class.forName("com.apple.eawt.event.GestureListener")
        val listener =
            Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
                when (method.name) {
                    "magnify" -> {
                        val event = args!![0]
                        onMagnify(event.javaClass.getMethod("getMagnification").invoke(event) as Double)
                        null
                    }

                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    "toString" -> "MagnificationListener"
                    else -> null
                }
            }
        utilities.getMethod("addGestureListenerTo", JComponent::class.java, gestureType).invoke(null, component, listener)
        true
    }.getOrDefault(false)
