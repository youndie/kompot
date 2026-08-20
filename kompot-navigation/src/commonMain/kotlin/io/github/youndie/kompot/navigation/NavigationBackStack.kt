package io.github.youndie.kompot.navigation

// The stack of visited deeplinks of graph-driven screens (see ScreenRoute/NavigationGraph). It is a
// plain immutable value: every push/pop returns a new stack, the same principle as WizardSession in
// :wizard-core. The client holds a variable and reassigns it, so there is nothing UI-framework
// specific here and the stack is covered by ordinary unit tests. An application wraps it in whatever
// its own UI layer uses to trigger a redraw.
data class NavigationBackStack(
    val entries: List<String>,
) {
    init {
        require(entries.isNotEmpty()) { "NavigationBackStack must have at least one entry" }
    }

    constructor(initial: String) : this(listOf(initial))

    val current: String get() = entries.last()

    val canGoBack: Boolean get() = entries.size > 1

    // Pushing the deeplink already on top is a no-op: consecutive duplicates would make Back return
    // to the very screen it was invoked from.
    fun push(deeplink: String): NavigationBackStack = if (deeplink == current) this else NavigationBackStack(entries + deeplink)

    // On a single-entry stack pop is a no-op — there is nowhere to return to. What to do instead is
    // the caller's decision; an application usually leaves graph-driven navigation altogether.
    fun pop(): NavigationBackStack = if (canGoBack) NavigationBackStack(entries.dropLast(1)) else this
}
