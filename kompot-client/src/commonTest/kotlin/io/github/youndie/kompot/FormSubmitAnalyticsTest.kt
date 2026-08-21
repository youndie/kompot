package io.github.youndie.kompot

import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.forms.SubmitFormAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import kotlin.test.Test
import kotlin.test.assertEquals

private fun submitTestSchema() =
    FormSchema(
        formId = "transfer",
        fields = listOf(TestField(fieldId = "amount", rules = listOf(TestRequiredRule("required")))),
    )

class FormSubmitAnalyticsTest {
    @Test
    fun `submitting with an empty required field tracks Attempted then Blocked`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val tracker = AnalyticsTracker { tracked += it }
        val controller = FormController(submitTestSchema())
        var forwarded: KompotAction? = null
        val handler =
            KompotActionHandler { forwarded = it }
                .withFormSubmitTracking(controller, tracker, formId = "transfer")

        handler.handle(SubmitFormAction(formId = "transfer"))

        assertEquals(listOf(AnalyticsEvent.FormSubmitAttempted("transfer"), AnalyticsEvent.FormSubmitBlocked("transfer")), tracked)
            assertEquals(SubmitFormAction(formId = "transfer"), forwarded) // always forwarded, even when blocked
    }

    @Test
    fun `submitting with a valid field tracks Attempted then Succeeded`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val tracker = AnalyticsTracker { tracked += it }
        val controller = FormController(submitTestSchema())
        controller.onValueChanged("amount", TestValue("100"))
        val handler = KompotActionHandler {}.withFormSubmitTracking(controller, tracker, formId = "transfer")

        handler.handle(SubmitFormAction(formId = "transfer"))

        assertEquals(listOf(AnalyticsEvent.FormSubmitAttempted("transfer"), AnalyticsEvent.FormSubmitSucceeded("transfer")), tracked)
    }

    @Test
    fun `non-submit actions are forwarded without tracking anything`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val tracker = AnalyticsTracker { tracked += it }
        val controller = FormController(submitTestSchema())
        var forwarded: KompotAction? = null
        val handler = KompotActionHandler { forwarded = it }.withFormSubmitTracking(controller, tracker, formId = "transfer")

        handler.handle(NavigateAction(deeplink = "/home"))

        assertEquals(emptyList<AnalyticsEvent>(), tracked)
        assertEquals(NavigateAction(deeplink = "/home"), forwarded)
    }
}
