package io.github.youndie.kompot

import io.github.youndie.kompot.standard.RefreshAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs `refresh` (SPEC.md §16.4): [reload] fetches the screen being shown again, and the application
 * hands the new tree to the same `KompotScreen` / `KompotLazyScreen`. No navigation happens, so the
 * stack does not grow; nodes keep their state under their stable ids (§4.4), so a list keeps its place.
 *
 * The tree [reload] brings is an arrival even when it is equal to the drawn one, and it drops the
 * screen's overrides — live frames, `update`s — like any other (§4.4). Under [KompotScreenLoader] that
 * is a new key under the same `screenKey` (a counter in the key), whose completed load is the arrival;
 * a screen drawn without the loader gets a new `arrival` together with the tree.
 *
 * What "the screen being shown" is — its endpoint, its cache — is the application's, the same as for
 * `navigate`; the toolkit takes no opinion on HTTP. The action is forwarded, as `withPerform` forwards,
 * for an analytics wrapper further along the chain.
 */
public fun KompotActionHandler.withRefresh(
    scope: CoroutineScope,
    reload: suspend () -> Unit,
): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is RefreshAction) scope.launch { reload() }
        handle(action)
    }
