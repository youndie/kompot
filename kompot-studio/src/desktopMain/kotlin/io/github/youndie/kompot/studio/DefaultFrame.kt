package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.ds.material.rememberMaterialColorScheme
import io.github.youndie.kompot.theme.KompotTheme
import io.github.youndie.kompot.theme.client.RemoteThemeDesignSystem
import kotlinx.serialization.json.Json
import java.io.File

// THE FRAME FOR A PROJECT THAT HAS NO BRAND COMPOSITION OF ITS OWN, and only for that. A deployment
// with its own theme hands over its own frame — this one exists so that the studio is usable on the
// first day of a project, and so that the toolkit's own components can be looked at without one.
//
// It does both halves of a brand, which is the mistake worth not repeating: a design system alone
// leaves every Material control — the button's fill, the field's outline — in the stock purple, and a
// MaterialTheme alone leaves every token the renderers resolve at the built-in value. konekt found
// this the hard way; here they are one function so they cannot be applied one at a time.
public fun kompotStudioFrame(themes: Map<String, KompotTheme> = emptyMap()): KompotStudioFrame =
    { brand, dark, content ->
        val theme = themes[brand]

        // darkModeOverride and not the system setting: a preview has no system signal worth obeying —
        // the switch above the window is the signal, and it is the reason this parameter exists.
        val designSystem =
            remember(theme, dark) {
                if (theme == null) {
                    Material3DesignSystem()
                } else {
                    RemoteThemeDesignSystem(theme, Material3DesignSystem(), darkModeOverride = dark)
                }
            }

        MaterialTheme(colorScheme = rememberMaterialColorScheme(theme, dark)) {
            // Inside a Surface, because outside one LocalContentColor is black: any control that
            // colours its own text draws black on dark, and the window looks almost right in the one
            // way that is hard to see.
            Surface(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalKompotDesignSystem provides designSystem) { content() }
            }
        }
    }

// The themes of a project, read as the server serves them: wire KompotTheme, one file per brand.
//
// Keyed by FILE NAME rather than by KompotTheme.id, and the two are not the same thing: the id is
// diagnostics only ("which theme actually arrived"), while the name a person picks in the studio is
// the name of the file they edit. Keying by id would silently merge two files that forgot to change
// it — a rename away from becoming one brand.
public fun kompotThemesFrom(
    directory: File,
    json: Json = Json { ignoreUnknownKeys = true },
): Map<String, KompotTheme> =
    (directory.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: emptyArray())
        .sortedBy { it.name }
        .associate { file ->
            file.name.removeSuffix(".json") to json.decodeFromString(KompotTheme.serializer(), file.readText())
        }
