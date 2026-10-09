package io.github.youndie.kompot.ktor

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.commands.kompotCommandsSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.github.youndie.kompot.standard.text
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import kotlin.test.Test
import kotlin.test.assertEquals

private val updateJson =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule + kompotStandardSerializersModule + generatedStandardSerializersModule +
            kompotCommandsSerializersModule
    }

// A load endpoint answers with an update; the helper writes it with the screen DSL and keeps the root's
// discriminator, the trap respondKompotAction exists for.
class RespondKompotUpdateTest {
    @Test
    fun `an update written with the DSL reaches the client as one`() =
        testApplication {
            routing {
                get("/ui/catalog/results") {
                    call.respondKompotUpdate(updateJson, deeplink = "app://catalog?brand=acme") {
                        text("acme only", id = "results")
                    }
                }
            }

            val decoded =
                updateJson.decodeFromString(
                    PolymorphicSerializer(KompotAction::class),
                    client.get("/ui/catalog/results").bodyAsText(),
                )

            assertEquals(
                UpdateAction(
                    updates =
                        listOf(
                            UpdateComponentMessage("results", TextComponent(id = "results", text = "acme only")),
                        ),
                    deeplink = "app://catalog?brand=acme",
                ),
                decoded,
            )
            assertEquals(UpdateHistory.PUSH, UpdateHistory.of((decoded as UpdateAction).history))
        }
}
