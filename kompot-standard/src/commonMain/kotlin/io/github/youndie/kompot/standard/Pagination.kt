package io.github.youndie.kompot.standard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker

// A paginated list container: the server sends the first page of items inside the screen body
// (initialItems), and the client loads the rest through loadMoreAction/KompotPageResponse.
//
// reloadUrl is optional. When set, the renderer watches the FormController of the same form for
// CHANGES — the renderer receives the same controller as the filter fields do — and re-requests the
// first page with the current field values as query parameters. The key idea: filters, search and
// sorting are just neighbouring fields of the same FormSchema, and need no mechanism of their own.
@Serializable
@SerialName("paginated_list")
@KompotComponentMarker
public data class PaginatedListComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val initialItems: List<@Polymorphic KompotComponent>,
    val loadMoreAction: LoadPageAction? = null,
    val reloadUrl: String? = null,
    val emptyState: @Polymorphic KompotComponent? = null,
) : KompotComponent

// The "load the next page" action. Typed concretely rather than as a @Polymorphic KompotAction: the
// list renderer calls the url itself and needs no dispatch through KompotActionHandler for this
// particular step.
@Serializable
@SerialName("load_page")
public data class LoadPageAction(
    val url: String,
) : KompotAction

// The page response, used both for "next page" and for "first page with new filters". Items REPLACE
// the current list on a filter reload and are APPENDED when loading a further page — the client
// decides which, not the server. nextLoadAction == null means there is no more data.
@Serializable
public data class KompotPageResponse(
    val items: List<@Polymorphic KompotComponent>,
    val nextLoadAction: LoadPageAction? = null,
)

// The contract for fetching a page over the network, implemented on the client over whichever HTTP
// client is in use. The list renderer knows nothing about HTTP, only about this contract — the same
// idea as the remote data source resolver behind autocomplete.
public interface KompotPageLoader {
    public suspend fun loadPage(
        url: String,
        params: Map<String, String> = emptyMap(),
    ): KompotPageResponse
}
