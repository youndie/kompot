package io.github.youndie.kompot.realtime

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotComponent

// A screen that names the channel its updates arrive on.
//
// The topic used to live in exactly one place in the whole protocol — KompotFormResponse — and that
// envelope also requires a schema. So a screen that is not a form and wants live updates had to be
// sent as a form response carrying a form that does not exist:
//
//     KompotFormResponse(
//         schema = FormSchema(formId = sweep.id, fields = emptyList()),  // invented to carry one string
//         screen = SweepScreen.of(sweep, results),
//         realtimeTopic = "sweep:${'$'}{sweep.id}",
//     )
//
// The reading side then holds a form engine bound to nothing, and a client that reasonably treats a
// form response as "there is a form here" is wrong about this screen. The screens that most want live
// updates tend not to be forms at all: a list filling in as work completes, a dashboard, a status
// page — a form is the case where the CLIENT is doing the changing.
//
// Here rather than in :kompot-core because the field exists for the channel and the channel is §10;
// a screen with nothing to say about updates still travels as a bare component tree, which is also
// what keeps §16.2 caching it as before.
@Serializable
data class KompotScreenResponse(
    val screen: @Polymorphic KompotComponent,
    // Per response rather than per screen: the server decides it per request ("sweep:$id"), so two
    // people looking at the same screen can be on different channels. null says this particular
    // response has no channel, which is the same thing a bare component tree says.
    val realtimeTopic: String? = null,
)
