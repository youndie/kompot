package io.github.youndie.kompot.ktor

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.kompotCoreSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
@SerialName("etag_test_component")
private data class ETagTestComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val label: String,
) : KompotComponent

@Serializable
private data class ETagTestDto(
    val value: String,
)

private val etagTestJson =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule +
            SerializersModule {
                polymorphic(KompotComponent::class) {
                    subclass(ETagTestComponent::class)
                }
            }
    }

class ETagResponsesTest {
    @Test
    fun `the first request returns 200 with an ETag header`() =
        testApplication {
            routing {
                get("/component") {
                    call.respondKompotComponentCached(etagTestJson, ETagTestComponent(id = "root", label = "hello"))
                }
            }

            val response = client.get("/component")

            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(response.headers["ETag"])
        }

    @Test
    fun `a matching If-None-Match returns 304 with no body`() =
        testApplication {
            routing {
                get("/component") {
                    call.respondKompotComponentCached(etagTestJson, ETagTestComponent(id = "root", label = "hello"))
                }
            }

            val etag = client.get("/component").headers["ETag"]!!
            val second = client.get("/component") { header("If-None-Match", etag) }

            assertEquals(HttpStatusCode.NotModified, second.status)
            assertTrue(second.bodyAsText().isEmpty())
        }

    @Test
    fun `a mismatched If-None-Match returns 200 with the fresh body`() =
        testApplication {
            routing {
                get("/component") {
                    call.respondKompotComponentCached(etagTestJson, ETagTestComponent(id = "root", label = "hello"))
                }
            }

            val response = client.get("/component") { header("If-None-Match", "\"stale-etag\"") }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("\"label\":\"hello\""))
        }

    @Test
    fun `respondCached works for a plain serializable type, not just KompotComponent`() =
        testApplication {
            routing {
                get("/dto") {
                    call.respondCached(etagTestJson, ETagTestDto(value = "hello"))
                }
            }

            val first = client.get("/dto")
            val etag = first.headers["ETag"]!!
            val second = client.get("/dto") { header("If-None-Match", etag) }

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.NotModified, second.status)
        }
}
