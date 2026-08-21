package io.github.youndie.kompot.analytics

// A flat "event name plus properties" — what a component or an action becomes after passing through
// the naming registry. This is what a tracker sees, not the component itself.
data class KompotEventDescriptor(
    val eventName: String,
    val properties: Map<String, String> = emptyMap(),
)

// A closed hierarchy: the set of events this module can emit is known in advance and is not extended
// from outside, unlike the open component and action hierarchies. Sealed gives an implementation an
// exhaustive `when` with no stray "unknown event" branch.
sealed class AnalyticsEvent {
    data class ScreenView(
        val screenName: String,
    ) : AnalyticsEvent()

    data class ActionTracked(
        val descriptor: KompotEventDescriptor,
    ) : AnalyticsEvent()

    data class ComponentImpression(
        val descriptor: KompotEventDescriptor,
    ) : AnalyticsEvent()

    // The user saw the RESULT of an experiment. A case of its own rather than an entry in a
    // descriptor's properties, for two reasons: an exposure may relate to several components of a
    // screen at once rather than to one node being shown, and analytics needs to filter and aggregate
    // by experiment and variant directly, without parsing arbitrary properties.
    //
    // variantId is what the assigner returned, not a Variant: this module does not depend on
    // :experiments-core, on the same principle that keeps concrete types in their plug-in.
    data class ExperimentExposure(
        val experimentId: String,
        val variantId: String,
    ) : AnalyticsEvent()

    data class FieldChanged(
        val formId: String,
        val fieldId: String,
    ) : AnalyticsEvent()

    data class FieldValidationErrorShown(
        val formId: String,
        val fieldId: String,
        val message: String,
    ) : AnalyticsEvent()

    data class FormSubmitAttempted(
        val formId: String,
    ) : AnalyticsEvent()

    // getPayload() == null: at least one visible field failed validation.
    data class FormSubmitBlocked(
        val formId: String,
    ) : AnalyticsEvent()

    // getPayload() != null: client-side validation passed. NOT a confirmation from the server that
    // the operation happened — only that the submit was not blocked locally.
    data class FormSubmitSucceeded(
        val formId: String,
    ) : AnalyticsEvent()
}
