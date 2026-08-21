package io.github.youndie.kompot.spec

// Constants and naming rules shared by the schema generator and the conformance tests.
object KompotProtocol {
    // The same value an application configures as classDiscriminator on its Json. It lives here
    // because it is part of the protocol rather than a detail of one Json instance, and a test checks
    // that a really serialised component carries a property with exactly this name.
    const val DISCRIMINATOR = "type"

    const val SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema"

    // The $id prefix. It resolves to nothing over the network; it exists so that relative $refs
    // between files ("kompot-core.schema.json#/${'$'}defs/X") have an unambiguous base for validators.
    const val ID_PREFIX = "https://kompot.workinprogress.ru/schema/"

    const val PROFILE_FILE_NAME = "kompot.profile.schema.json"

    // The HTTP layer: addresses, status codes, headers. A file of its own rather than a section of the
    // schema, because it describes one concrete server, while the *.schema.json files describe the
    // protocol itself.
    const val OPENAPI_FILE_NAME = "kompot.openapi.json"

    // The entry point of the reference corpus: it lists every body together with what is expected of
    // it, so a harness on any stack walks the corpus by the manifest rather than by a list of its own.
    const val EXAMPLES_INDEX_FILE_NAME = "index.json"

    fun fileNameFor(moduleName: String) = "$moduleName.schema.json"

    // ---- formats that cannot be derived from a Kotlin type (all of these are declared String) ----

    // The identifier of an APPLICATION SCREEN: a URI with the application's own scheme — "app://home",
    // "myapp://checkout?tariff=premium". Neither the scheme nor the set of values is fixed by the
    // protocol: both belong to the application rather than to the toolkit (see SPEC.md §12.2). The one
    // thing that really is a rule of the protocol is fixed here: a deeplink is NOT a web address. Hence
    // the negative lookahead on http/https — without it a server could take the client to an external
    // page through an ordinary navigate.
    const val DEEPLINK_PATTERN = "^(?!https?:)[a-z][a-z0-9+.-]*://[^\\s]*${'$'}"

    // An address on the same host as the rest of the API — always relative.
    const val ENDPOINT_PATTERN = "^/[^\\s#]*${'$'}"

    // The topic of the live-update channel: a scope and, where the data is personal, a subject —
    // "home:user1", "orders:user1". The string is opaque to the client.
    const val REALTIME_TOPIC_PATTERN = "^[a-z][a-z0-9_]*(:[A-Za-z0-9._-]+)*${'$'}"

    // Reserved rawMetadata keys: these are read by the protocol's own mechanisms rather than by
    // application code (see SPEC.md §9.7), so they must not be reused with another meaning.
    const val METADATA_KEY_BALANCE = "balance"
    const val METADATA_KEY_CURRENCY = "currency"
}

// The serialName of a nullable descriptor ends in "?" (SerialDescriptorForNullable); without this,
// "TypographyToken?" would become a schema key of its own.
internal fun String.withoutNullMark() = removeSuffix("?")

// The schema key for a type met as a CONCRETE (non-polymorphic) one: the last segment of serialName.
// For a type without @SerialName that is the Kotlin class name ("TableRow"); for one with it, the wire
// name ("load_page"), which is turned into PascalCase ("LoadPage").
internal fun bareKey(serialName: String): String {
    val last = serialName.withoutNullMark().substringAfterLast('.')
    return if (last.contains('_') || last.firstOrNull()?.isLowerCase() == true) pascal(last) else last
}

// The schema key for a type met as a MEMBER of a polymorphic hierarchy. The hierarchy prefix is
// mandatory: the same wire type could in principle exist in two hierarchies, and code generation from
// schema keys must produce class names that do not collide.
//
// substringAfterLast('.') is a safeguard for types WITHOUT @SerialName, whose wire name becomes the
// full Kotlin class name. Such a type in the protocol is a mistake (see SPEC.md §13.1, where it
// happened once already), but the schema key must not break because of it. The wire name itself stays
// untouched in "type": const — only the schema key is trimmed.
internal fun memberKey(
    hierarchy: String,
    wireName: String,
) = hierarchy + pascal(wireName.withoutNullMark().substringAfterLast('.'))

fun pascal(wireName: String) =
    wireName
        .withoutNullMark()
        .split('_')
        .filter { it.isNotEmpty() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

// The descriptor of a field declared as @Polymorphic Base has a serialName of the form
// "kotlinx.serialization.Polymorphic<KompotComponent>": the base name is pulled out of it to refer to
// the open base schema (see the hand-written part of the kompot-core module definition).
internal fun openHierarchyName(serialName: String): String =
    serialName
        .withoutNullMark()
        .substringAfter('<')
        .substringBeforeLast('>')
        .substringAfterLast('.')
