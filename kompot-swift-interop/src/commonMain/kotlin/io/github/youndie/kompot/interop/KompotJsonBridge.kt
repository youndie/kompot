package io.github.youndie.kompot.interop

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.encodeKompotAction
import io.github.youndie.kompot.decodeKompotAction
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.forms.KompotFormResponse
import io.github.youndie.kompot.forms.FormPatchRequest
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.wizard.WizardResumeRequest
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormPatch
import io.github.youndie.kompot.wizard.core.WizardTransition

// Non-generic wrappers over the PolymorphicSerializer(X::class) pattern that respondKompotComponent
// in :kompot-ktor already uses. A reified decodeFromString<T>() plays the same role on a JVM client,
// but reified and generic calls are not exported to Swift at all — the Kotlin/Native ObjC export
// simply does not publish such signatures.
//
// Every function takes the Json as a parameter. Which concrete types it knows is the application's
// business: this module carries a tree without knowing one component type in it.
public fun decodeKompotComponent(
    json: Json,
    text: String,
): KompotComponent = json.decodeKompotComponent(text)

public fun encodeKompotComponent(
    json: Json,
    component: KompotComponent,
): String = json.encodeKompotComponent(component)

public fun decodeKompotAction(
    json: Json,
    text: String,
): KompotAction = json.decodeKompotAction(text)

public fun encodeKompotAction(
    json: Json,
    action: KompotAction,
): String = json.encodeKompotAction(action)

// KompotFormResponse and FormPatchRequest are not polymorphic themselves — plain @Serializable data
// classes whose compiler-generated serializer() is available directly, no PolymorphicSerializer
// needed. But decodeFromString<T>()/encodeToString<T>() are still reified, the same export gap as
// above, so they need the same non-generic wrappers.
public fun decodeKompotFormResponse(
    json: Json,
    text: String,
): KompotFormResponse = json.decodeFromString(KompotFormResponse.serializer(), text)

public fun encodeFormPatchRequest(
    json: Json,
    formId: String,
    fieldId: String,
    values: Map<String, FieldValue>,
): String = json.encodeToString(FormPatchRequest.serializer(), FormPatchRequest(formId, fieldId, values))

// KompotPageResponse is not polymorphic itself either — only its nested
// `items: List<@Polymorphic KompotComponent>` is — the same reified gap as above.
public fun decodeKompotPageResponse(
    json: Json,
    text: String,
): KompotPageResponse = json.decodeFromString(KompotPageResponse.serializer(), text)

// FormPatch is the answer to a patch request, and hits the same non-generic gap.
public fun decodeFormPatch(
    json: Json,
    text: String,
): FormPatch = json.decodeFromString(FormPatch.serializer(), text)

// WizardResumeRequest is the body of a wizard's resume request (the server decides the address). It
// is not polymorphic itself, but its `values: Map<String, @Polymorphic FieldValue>` needs polymorphic
// serialisation inside, and the compiler generates a non-reified serializer() for it anyway — the
// same gap as encodeFormPatchRequest above. Swift builds a WizardTransition from the exported Kotlin
// classes directly (WizardTransition.Next.shared and friends) and needs no factory here.
public fun encodeWizardResumeRequest(
    json: Json,
    transition: WizardTransition,
    values: Map<String, FieldValue>,
): String = json.encodeToString(WizardResumeRequest.serializer(), WizardResumeRequest(transition, values))
