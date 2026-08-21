package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.JsonSchemaValidator
import io.github.youndie.kompot.navigation.ScreenRouteKind
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.collectJsonObjects
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// One violation of the protocol. The checks raise nothing and accumulate findings instead: a run must
// reach the end and show EVERY discrepancy at once — an implementation on another stack would rather
// fix them in a batch than one per run.
data class TckFinding(
    val check: String,
    val target: String,
    val message: String,
) {
    override fun toString() = "[$check] $target — $message"
}

// An endpoint the walk never looked at, and why. The per-check counters cannot show this: they answer
// "did this check have targets", and the other endpoints keep every check busy while one is quietly
// left out. A run that is green because it skipped the hardest screen is the failure this closes.
data class TckSkip(
    val method: String,
    val path: String,
    val reason: String,
) {
    override fun toString() = "$method $path ($reason)"
}

// The report of a run: the findings, plus how many targets each check actually visited, plus what was
// not visited at all.
data class TckReport(
    val findings: List<TckFinding>,
    val exercised: Map<String, Int>,
    val skipped: List<TckSkip> = emptyList(),
    // Printed in the report on purpose: an extension weakens a strict check, and that must be visible
    // in the output of a green run rather than living quietly in a config.
    val declaredExtensions: Set<String> = emptySet(),
) {
    val isClean: Boolean get() = findings.isEmpty()

    override fun toString(): String {
        val head =
            if (isClean) {
                "TCK: no violations. Checks: " + exercised.entries.joinToString { "${it.key}=${it.value}" } +
                    if (declaredExtensions.isEmpty()) "" else ". Deployment extensions: " + declaredExtensions.sorted().joinToString()
            } else {
                "TCK: ${findings.size} violations\n" + findings.joinToString("\n")
            }

        // Printed on a clean run too, and that is the whole point: silence about what was not walked
        // reads exactly like coverage.
        return if (skipped.isEmpty()) head else head + "\nNot walked:\n" + skipped.joinToString("\n") { "  $it" }
    }
}

// Everything the kit cannot know about the server it is pointed at. Nothing here has a default that
// belongs to one particular application: a library that ships someone's login path is a library that
// knows an application.
data class TckConfig(
    // The spec of the build being checked: every schema file by name, including the profile. Read it
    // from the classpath with KompotSpecResources, or hand over documents assembled any other way.
    val schemas: Map<String, JsonObject>,
    // The description of the server under test. A different stack supplies its own — the kit reads
    // endpoint kinds out of it and never assumes an address (see SPEC.md §16).
    val openApi: JsonObject,
    // The endpoint the kit gets a token through: an ordinary submit answering with update_session.
    // null means the run stays anonymous, and every check that needs a token is skipped.
    val loginPath: String? = null,
    val loginValues: Map<String, JsonElement> = emptyMap(),
    // Bodies for submit endpoints: what exactly to send is the application's domain, and the kit
    // cannot guess it. The idempotency check runs only for the paths listed here.
    val submitPayloads: Map<String, JsonElement> = emptyMap(),
    // The idempotency check performs a REAL operation — there is no other way to reach 400/409, since
    // a route validates fields first. Run it against a test environment only.
    val allowStateChangingChecks: Boolean = true,
    // Wire types this deployment adds on top of the profile: the actions and components of product
    // features. The profile is the toolkit's vocabulary and knows nothing about them, so a server with
    // a feature of its own must DECLARE them here. Declared, not inferred from the responses —
    // otherwise the check "the server keeps to what it declared" would mean nothing.
    val extensionTypes: Set<String> = emptySet(),
    // Which property of a rule or condition holds a reference to a NEIGHBOURING field, by wire type.
    // The field plug-in is the application's, so its rule names are too: for the reference field set
    // this is "equals" to "fieldId", "required_if" to "targetFieldId" and so on. An empty map leaves
    // the cross-reference half of the form check idle — the report says so.
    val crossReferenceKeys: Map<String, String> = emptyMap(),
)

class TckRunner(
    private val transport: TckTransport,
    private val config: TckConfig,
) {
    private val json = Json { prettyPrint = false }
    private val schemas = config.schemas
    private val profile = schemas.getValue(KompotProtocol.PROFILE_FILE_NAME)
    private val validator = JsonSchemaValidator(schemas, strictProfile = profile, extensionTypes = config.extensionTypes)
    private val endpoints = TckEndpoints.fromOpenApi(config.openApi)

    private val componentTypes: Set<String> = discriminatorsOf("KompotComponent")

    private var token: String? = null

    private val exercised = sortedMapOf<String, Int>()

    // Which endpoints a check actually reached, as "METHOD path". Derived from the walk rather than
    // from a second list of predicates: a list would drift the moment a check widened its reach, and
    // would then under-report the very thing it exists to report.
    private val visited = mutableSetOf<String>()

    suspend fun run(): TckReport {
        val findings = mutableListOf<TckFinding>()

        findings += authenticate()
        findings += securedEndpointsRejectAnonymous()
        findings += responsesMatchSchema()
        findings += componentIdsPresentAndUnique()
        findings += formsAreSelfConsistent()
        findings += etagRevalidation()
        findings += paginationTerminates()
        findings += navigationGraphResolves()
        findings += performTargetsAreSubmitEndpoints()
        findings += idempotencyContract()

        return TckReport(findings, exercised.toMap(), notWalked(), config.extensionTypes)
    }

    // Every check records how many targets it actually visited. Without that a green run means
    // nothing: a check that found no matching endpoint passes silently, which is the commonest way to
    // end up with a useless conformance kit.
    private fun <T> Iterable<T>.exercising(check: String): Iterable<T> =
        also { targets -> exercised[check] = (exercised[check] ?: 0) + targets.count() }

    // ---- checks --------------------------------------------------------------------------------

    // Not a check of the protocol but a precondition of the rest: without a token the secured
    // endpoints are out of reach.
    private suspend fun authenticate(): List<TckFinding> {
        val loginPath = config.loginPath ?: return emptyList()
        if (config.loginValues.isEmpty()) return emptyList()

        val body =
            buildJsonObjectOf(
                "formId" to JsonPrimitive("login"),
                "fieldId" to JsonPrimitive("login"),
                "values" to JsonObject(config.loginValues),
            )
        visited += "POST $loginPath"
        val response = transport.request("POST", loginPath, body = json.encodeToString(JsonObject.serializer(), body))

        if (response.status != 200) {
            return listOf(TckFinding("auth", loginPath, "login failed: ${response.status} ${response.body.take(200)}"))
        }

        val action = parse(response.body)?.jsonObject
        val accessToken = (action?.get("accessToken") as? JsonPrimitive)?.content
        if (action == null || (action[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content != "update_session" || accessToken == null) {
            return listOf(TckFinding("auth", loginPath, "the login response is not an update_session carrying an accessToken"))
        }

        token = accessToken
        return emptyList()
    }

    // A secured endpoint must answer 401 without a token, or personal data is available to everyone.
    private suspend fun securedEndpointsRejectAnonymous(): List<TckFinding> =
        probeable().filter { it.secured }.exercising("auth-required").mapNotNull { endpoint ->
            val response = transport.request(endpoint.method, endpoint.path)
            if (response.status == 401) {
                null
            } else {
                TckFinding("auth-required", endpoint.path, "expected 401 without a token, got ${response.status}")
            }
        }

    // The body of a successful response must match the schema declared for that endpoint.
    private suspend fun responsesMatchSchema(): List<TckFinding> =
        probeable().exercising("schema").flatMap { endpoint ->
            val response = get(endpoint)
            val schema = endpoint.successSchema

            when {
                response.status != endpoint.successStatus ->
                    listOf(TckFinding("status", endpoint.path, "expected ${endpoint.successStatus}, got ${response.status}"))

                schema == null -> emptyList()

                else -> {
                    val element = parse(response.body)
                    if (element == null) {
                        listOf(TckFinding("schema", endpoint.path, "the body is not valid JSON"))
                    } else {
                        validator.validate(element, schema).map { TckFinding("schema", endpoint.path, it) }
                    }
                }
            }
        }

    // A node's id addresses point updates (SPEC.md §4.2): an empty or duplicated id makes the address
    // ambiguous, and a frame of the update channel lands on the wrong node.
    private suspend fun componentIdsPresentAndUnique(): List<TckFinding> =
        probeable().exercising("component-id").flatMap { endpoint ->
            val element = parse(get(endpoint).body) ?: return@flatMap emptyList()
            val ids = mutableListOf<String>()
            val findings = mutableListOf<TckFinding>()

            collectJsonObjects(element)
                .filter { (it[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content in componentTypes }
                .forEach { component ->
                    val id = (component["id"] as? JsonPrimitive)?.content
                    val type = (component[KompotProtocol.DISCRIMINATOR] as JsonPrimitive).content
                    if (id.isNullOrBlank()) {
                        findings += TckFinding("component-id", endpoint.path, "component \"$type\" has an empty id")
                    } else {
                        ids += id
                    }
                }

            findings +
                ids
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                    .sorted()
                    .map { TckFinding("component-id", endpoint.path, "id \"$it\" occurs more than once in the tree") }
        }

    // A form's schema and its screen must agree on fieldId, and the cross-references of rules and
    // conditions must point at fields that exist (SPEC.md §9.2, §9.3).
    private suspend fun formsAreSelfConsistent(): List<TckFinding> =
        probeable().filter { it.kind == "form" }.exercising("form-fields").flatMap { endpoint ->
            val response = parse(get(endpoint).body)?.jsonObject ?: return@flatMap emptyList()
            val schema = response["schema"]?.jsonObject ?: return@flatMap emptyList()
            val screen = response["screen"] ?: return@flatMap emptyList()

            val declared =
                (schema["fields"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { (it.jsonObject["fieldId"] as? JsonPrimitive)?.content }
                    .toSet()

            val referenced =
                collectJsonObjects(screen)
                    .mapNotNull { (it["fieldId"] as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
                    .toSet()

            val crossReferences =
                collectJsonObjects(schema).mapNotNull { obj ->
                    val type = (obj[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val key = config.crossReferenceKeys[type] ?: return@mapNotNull null
                    (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                }.toSet()

            (referenced - declared).map { TckFinding("form-fields", endpoint.path, "a component refers to undeclared field \"$it\"") } +
                (declared - referenced).map { TckFinding("form-fields", endpoint.path, "field \"$it\" is declared but never rendered") } +
                (crossReferences - declared).map { TckFinding("form-fields", endpoint.path, "a cross-reference points at non-existent field \"$it\"") }
        }

    // A perform action names the address it posts to, and SPEC.md §16.4 requires that address to be an
    // endpoint of kind `submit` — that is what makes it answer a KompotAction the client can feed back
    // into its chain. No schema can check this: the url is a string, and only the application's own
    // HTTP description knows what lives behind it. Static, so unlike the idempotency check it needs no
    // permission to change state.
    private suspend fun performTargetsAreSubmitEndpoints(): List<TckFinding> =
        probeable().exercising("perform").flatMap { endpoint ->
            val element = parse(get(endpoint).body) ?: return@flatMap emptyList()

            collectJsonObjects(element)
                .filter { (it[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content == KompotProtocol.ACTION_PERFORM }
                .mapNotNull { (it["url"] as? JsonPrimitive)?.takeIf { url -> url.isString }?.content }
                .distinct()
                .mapNotNull { url ->
                    val target = endpoints.firstOrNull { it.path == url && it.method == "POST" }
                    when {
                        target == null ->
                            TckFinding("perform", endpoint.path, "a perform action posts to \"$url\", which the HTTP description does not declare")

                        target.kind != "submit" ->
                            TckFinding(
                                "perform",
                                endpoint.path,
                                "a perform action posts to \"$url\", declared as kind \"${target.kind}\" rather than \"submit\"",
                            )

                        else -> null
                    }
                }
        }

    // Conditional delivery: a repeat with the same ETag must answer 304 with no body (SPEC.md §16.2).
    private suspend fun etagRevalidation(): List<TckFinding> =
        probeable().filter { 304 in it.statuses }.exercising("etag").flatMap { endpoint ->
            val first = get(endpoint)
            val etag =
                first.header("etag")
                    ?: return@flatMap listOf(TckFinding("etag", endpoint.path, "304 is declared but no ETag header arrived"))

            val second = get(endpoint, mapOf("If-None-Match" to etag))
            val findings = mutableListOf<TckFinding>()
            if (second.status != 304) findings += TckFinding("etag", endpoint.path, "a repeat with If-None-Match gave ${second.status} instead of 304")
            if (second.body.isNotEmpty()) findings += TckFinding("etag", endpoint.path, "the 304 response carried a body")

            // Without a stable body an ETag is pointless: it would change on every request.
            val third = get(endpoint)
            if (third.header("etag") != etag) findings += TckFinding("etag", endpoint.path, "the ETag of an unchanged screen differs between requests")

            findings
        }

    // Walking the pages must terminate: nextLoadAction == null sooner or later (SPEC.md §8.2).
    private suspend fun paginationTerminates(): List<TckFinding> =
        probeable().filter { it.kind == "page" }.exercising("pagination").flatMap { endpoint ->
            val findings = mutableListOf<TckFinding>()
            var url = endpoint.path
            var visited = 0

            while (visited < MAX_PAGES) {
                val response = transport.request("GET", url, authHeaders())
                val page = parse(response.body)?.jsonObject
                if (page == null) {
                    findings += TckFinding("pagination", url, "the page is not valid JSON")
                    break
                }
                endpoint.successSchema?.let { schema ->
                    findings += validator.validate(page, schema).map { TckFinding("pagination", url, it) }
                }

                val next = (page["nextLoadAction"] as? JsonObject)?.get("url") as? JsonPrimitive ?: return@flatMap findings
                url = next.content
                visited++
            }

            findings + TckFinding("pagination", endpoint.path, "the walk did not terminate within $MAX_PAGES pages")
        }

    // Every route of the graph must lead to a working screen, or a client that trusted the graph shows
    // an empty one (SPEC.md §12.1).
    private suspend fun navigationGraphResolves(): List<TckFinding> =
        probeable().filter { it.kind == "graph" }.exercising("navigation").flatMap { endpoint ->
            val graph = parse(get(endpoint).body)?.jsonObject ?: return@flatMap emptyList()
            val routes = graph["routes"] as? JsonArray ?: return@flatMap emptyList()

            routes.flatMap { route ->
                val target = (route.jsonObject["endpoint"] as? JsonPrimitive)?.content ?: return@flatMap emptyList()
                // A route without `kind` is a screen — the default in ScreenRoute, and what every graph
                // written before the field existed means.
                val routeKind = (route.jsonObject["kind"] as? JsonPrimitive)?.content ?: ScreenRouteKind.SCREEN
                val declared = endpoints.firstOrNull { it.path == target && it.method == "GET" }?.also { visited += it.key }
                val findings = mutableListOf<TckFinding>()

                // The route says what a client will parse the body as; the HTTP description says what
                // the server will send. Nothing else compares the two, and a disagreement is invisible
                // until a client hits the route: it decodes the wrong envelope and shows nothing.
                if (declared != null && declared.kind != routeKind) {
                    findings +=
                        TckFinding(
                            "navigation",
                            target,
                            "the route declares kind \"$routeKind\" while the endpoint is declared \"${declared.kind}\"",
                        )
                }

                val response = transport.request("GET", target, authHeaders(declared))

                when {
                    response.status != 200 -> findings + TckFinding("navigation", target, "a route of the graph answers ${response.status}")

                    else -> {
                        val element = parse(response.body)
                        // The schema to check against follows the KIND, not a hardcoded assumption that
                        // every route yields a component tree — which is what made a form route
                        // unreportable except as a false finding against it.
                        val schema = declared?.successSchema ?: SCREEN_SCHEMA.takeIf { routeKind == ScreenRouteKind.SCREEN }

                        when {
                            element == null -> findings + TckFinding("navigation", target, "the body is not valid JSON")
                            schema == null -> findings
                            else -> findings + validator.validate(element, schema).map { TckFinding("navigation", target, it) }
                        }
                    }
                }
            }
        }

    // An idempotency key is mandatory, and a repeat with the same key and a DIFFERENT body is a
    // conflict (SPEC.md §16.5).
    private suspend fun idempotencyContract(): List<TckFinding> {
        if (!config.allowStateChangingChecks) return emptyList()

        return endpoints
            .filter { it.kind == "submit" && 400 in it.statuses && 409 in it.statuses && it.path in config.submitPayloads }
            .exercising("idempotency")
            .flatMap { endpoint ->
                visited += endpoint.key
                val payload = config.submitPayloads.getValue(endpoint.path)
                val body = json.encodeToString(JsonElement.serializer(), payload)
                val findings = mutableListOf<TckFinding>()

                val withoutKey = transport.request("POST", endpoint.path, authHeaders(), body)
                if (withoutKey.status != 400) {
                    findings += TckFinding("idempotency", endpoint.path, "expected 400 without a key, got ${withoutKey.status}")
                }

                val key = "tck-" + body.hashCode().toString(16)
                transport.request("POST", endpoint.path, authHeaders() + mapOf(IDEMPOTENCY_HEADER to key), body)

                val different = json.encodeToString(JsonElement.serializer(), mutate(payload))
                val conflict = transport.request("POST", endpoint.path, authHeaders() + mapOf(IDEMPOTENCY_HEADER to key), different)
                if (conflict.status != 409) {
                    findings += TckFinding("idempotency", endpoint.path, "the same key with a different body gave ${conflict.status} instead of 409")
                }

                findings
            }
    }

    // ---- helpers -------------------------------------------------------------------------------

    // A blind GET applies only to an address with no placeholders that answers JSON. Streaming
    // endpoints — the update channel — are checked differently and stay out of this set (SPEC.md §16.9).
    private fun probeable() =
        endpoints
            .filter { it.method == "GET" && !it.hasPathParams && !it.deprecated && it.respondsWithJson }
            .onEach { visited += it.key }

    // Why an endpoint was left out, in the reader's terms rather than in the kit's. The reason is
    // explanatory; the SET comes from what the walk really touched.
    private fun notWalked(): List<TckSkip> =
        endpoints
            .filterNot { it.key in visited }
            .map { endpoint ->
                val reason =
                    when {
                        endpoint.deprecated -> "declared deprecated"
                        endpoint.hasPathParams -> "no value for the path parameters of \"${endpoint.path}\""
                        !endpoint.respondsWithJson ->
                            "the response is ${endpoint.successContentType ?: "not declared"}, not one JSON document"

                        endpoint.method != "GET" && endpoint.kind == "submit" && !config.allowStateChangingChecks ->
                            "state-changing checks are switched off"

                        endpoint.method != "GET" && endpoint.kind == "submit" ->
                            "no body for it in TckConfig.submitPayloads"

                        endpoint.method != "GET" -> "only GET endpoints are walked blind"
                        else -> "no check claims it"
                    }

                TckSkip(endpoint.method, endpoint.path, reason)
            }.sortedBy { it.path }

    private suspend fun get(
        endpoint: TckEndpoint,
        extraHeaders: Map<String, String> = emptyMap(),
    ) = transport.request(endpoint.method, endpoint.path, authHeaders(endpoint) + extraHeaders)

    private fun authHeaders(endpoint: TckEndpoint? = null): Map<String, String> {
        val required = endpoint?.secured ?: true
        val value = token
        return if (required && value != null) mapOf("Authorization" to "Bearer $value") else emptyMap()
    }

    private fun parse(body: String): JsonElement? = runCatching { json.parseToJsonElement(body) }.getOrNull()

    private fun discriminatorsOf(hierarchy: String): Set<String> {
        val definition = (profile.getValue("\$defs") as JsonObject)[hierarchy]?.jsonObject ?: return emptySet()
        return ((definition.getValue("discriminator") as JsonObject).getValue("mapping") as JsonObject).keys
    }

    // A second body for the conflict check: change the amount if there is one, otherwise add a marker.
    private fun mutate(payload: JsonElement): JsonElement {
        val root = payload.jsonObject
        val values = root["values"]?.jsonObject ?: return root
        val amountKey = values.keys.firstOrNull { key -> (values[key]?.jsonObject?.get("long")) != null }
        val mutatedValues =
            if (amountKey == null) {
                values + ("tck_marker" to buildJsonObjectOf(KompotProtocol.DISCRIMINATOR to JsonPrimitive("text_value"), "text" to JsonPrimitive("tck")))
            } else {
                val amount = values.getValue(amountKey).jsonObject
                val long = (amount.getValue("long") as JsonPrimitive).content.toLong()
                values + (amountKey to JsonObject(amount + ("long" to JsonPrimitive(long + 1))))
            }
        return JsonObject(root + ("values" to JsonObject(mutatedValues)))
    }

    private fun buildJsonObjectOf(vararg pairs: Pair<String, JsonElement>) = JsonObject(pairs.toMap())

    private companion object {
        const val MAX_PAGES = 50
        const val IDEMPOTENCY_HEADER = "Idempotency-Key"

        // The fallback for a route whose endpoint the HTTP description does not declare at all.
        const val SCREEN_SCHEMA = "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/KompotComponent"
    }
}
