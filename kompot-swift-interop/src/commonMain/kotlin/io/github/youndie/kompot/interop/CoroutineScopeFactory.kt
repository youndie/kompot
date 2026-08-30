package io.github.youndie.kompot.interop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// A FormController takes a CoroutineScope. On the Kotlin side that is usually a remembered scope or
// an ad hoc CoroutineScope(...); Swift has no way to assemble SupervisorJob() + Dispatchers.Main
// itself — those are kotlinx.coroutines internals and are not exported as constructible from
// ObjC/Swift. This factory is the single public entry through which Swift creates a scope for the
// lifetime of a screen.
//
// Cancelling it is the caller's business: a CoroutineScope has no such method of its own, so Swift
// normally just releases the controller together with the scope. Nothing has to wait for the child
// coroutines explicitly, because the scope never outlives the controller.
public fun mainCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
