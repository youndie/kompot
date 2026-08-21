package io.github.youndie.kompot.analytics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormFieldDefinition
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.ValidationRule
import kotlin.test.Test
import kotlin.test.assertEquals

private data class ObserverTestValue(
    val value: String,
) : FieldValue

private data class ObserverRequiredRule(
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean = value is ObserverTestValue && value.value.isNotBlank()
}

private data class ObserverFieldDefinition(
    override val fieldId: String,
    override val rules: List<ValidationRule>,
) : FormFieldDefinition

private fun observerTestSchema() =
    FormSchema(
        formId = "test_form",
        fields = listOf(ObserverFieldDefinition("name", listOf(ObserverRequiredRule("required")))),
    )

private class RecordingTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FormAnalyticsObserverTest {
    @Test
    fun `a value change followed by a blur validation error tracks FieldChanged then FieldValidationErrorShown`() =
        runTest {
            val controller = FormController(observerTestSchema())
            val tracker = RecordingTracker()

            val job = observeFormAnalytics(controller, tracker, this, formId = "test_form")

            controller.onValueChanged("name", ObserverTestValue(""))
            controller.onFieldBlurred("name")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    AnalyticsEvent.FieldChanged("test_form", "name"),
                    AnalyticsEvent.FieldValidationErrorShown("test_form", "name", "required"),
                ),
                tracker.events,
            )
            job.cancel()
        }

    @Test
    fun `mounting the observer on a pre-filled form does not report the initial value as a change`() =
        runTest {
            val controller = FormController(observerTestSchema(), initialValues = mapOf("name" to ObserverTestValue("Alice")))
            val tracker = RecordingTracker()

            val job = observeFormAnalytics(controller, tracker, this, formId = "test_form")
            advanceUntilIdle()

            assertEquals(emptyList<AnalyticsEvent>(), tracker.events)
            job.cancel()
        }

    @Test
    fun `re-setting the field to the same value does not track a duplicate FieldChanged`() =
        runTest {
            val controller = FormController(observerTestSchema())
            val tracker = RecordingTracker()

            val job = observeFormAnalytics(controller, tracker, this, formId = "test_form")

            controller.onValueChanged("name", ObserverTestValue("Alice"))
            advanceUntilIdle()
            controller.onValueChanged("name", ObserverTestValue("Alice"))
            advanceUntilIdle()

            assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.FieldChanged("test_form", "name")), tracker.events)
            job.cancel()
        }
}
