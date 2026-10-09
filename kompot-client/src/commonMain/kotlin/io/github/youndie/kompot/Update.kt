package io.github.youndie.kompot

import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.commands.UpdateHistory

/**
 * Runs `update` (SPEC.md §16.4): its frames go into [overrides], the store of the screen being shown,
 * in order — of two frames for one id the later wins, and a frame for an id the screen does not have
 * is ignored by the store itself (§10.2). The rest of the screen, and the state under every id, stays
 * as it was (§4.4).
 *
 * Nothing navigates. When the update names the screen's new address, [onAddress] gets it together
 * with how it enters the history — [UpdateHistory.PUSH] or [UpdateHistory.REPLACE], an unfamiliar word
 * already read as `push` — and the application, which keeps the history (§12), puts it there. The
 * nodes are written first, so an application that reads the screen from its address finds it current.
 *
 * [overrides] is the store the screen draws from: the one provided as [LocalKompotNodeOverrides] above
 * it. Put this under any handler whose answers may be an `update` — `withPerform`, `withLoad`, a form's
 * submit — since they hand their answers down the chain. The action is forwarded, as `withPerform`
 * forwards, for an analytics wrapper further along.
 */
public fun KompotActionHandler.withUpdates(
    overrides: KompotNodeOverrides,
    onAddress: (deeplink: String, history: String) -> Unit = { _, _ -> },
): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is UpdateAction) {
            action.updates.forEach { overrides.override(it.componentId, it.component) }
            action.deeplink?.let { onAddress(it, UpdateHistory.of(action.history)) }
        }
        handle(action)
    }
