@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.youndie.kompot

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import io.github.youndie.kompot.wizard.FinishWizardAction
import io.github.youndie.kompot.wizard.NextStepAction
import io.github.youndie.kompot.wizard.PrevStepAction
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.RequiredRule
import io.github.youndie.kompot.form.standard.TextFieldDefinition
import io.github.youndie.kompot.form.standard.TextValue
import kotlin.test.Test
import kotlin.test.assertEquals

class WizardSubmitTest {
    private val schema =
        FormSchema(
            formId = "mortgage",
            fields = listOf(TextFieldDefinition(fieldId = "full_name", rules = listOf(RequiredRule("required")))),
        )

    @Test
    fun `NextStepAction for a valid form calls onNext with the payload`() =
        runTest(UnconfinedTestDispatcher()) {
            val formController = FormController(schema, initialValues = mapOf("full_name" to TextValue("Ada")))
            var received: Map<String, FieldValue>? = null
            val handler =
                recordingActionHandler()
                    .withWizardNavigation(
                        scope = backgroundScope,
                        formController = formController,
                        formId = "mortgage",
                        onNext = { received = it },
                        onFinish = { error("not expected") },
                        onBack = { error("not expected") },
                    )

            handler.handle(NextStepAction("mortgage"))

            assertEquals(mapOf("full_name" to TextValue("Ada")), received)
        }

    @Test
    fun `NextStepAction for an invalid form does not call onNext`() =
        runTest(UnconfinedTestDispatcher()) {
            val formController = FormController(schema)
            var called = false
            val handler =
                recordingActionHandler()
                    .withWizardNavigation(
                        scope = backgroundScope,
                        formController = formController,
                        formId = "mortgage",
                        onNext = { called = true },
                        onFinish = { error("not expected") },
                        onBack = { error("not expected") },
                    )

            handler.handle(NextStepAction("mortgage"))

            assertEquals(false, called)
        }

    @Test
    fun `PrevStepAction calls onBack with the raw unvalidated values`() =
        runTest(UnconfinedTestDispatcher()) {
            val formController = FormController(schema)
            var called = false
            val handler =
                recordingActionHandler()
                    .withWizardNavigation(
                        scope = backgroundScope,
                        formController = formController,
                        formId = "mortgage",
                        onNext = { error("not expected") },
                        onFinish = { error("not expected") },
                        onBack = { called = true },
                    )

            handler.handle(PrevStepAction("mortgage"))

            assertEquals(true, called)
        }

    @Test
    fun `FinishWizardAction for a valid form calls onFinish`() =
        runTest(UnconfinedTestDispatcher()) {
            val formController = FormController(schema, initialValues = mapOf("full_name" to TextValue("Ada")))
            var called = false
            val handler =
                recordingActionHandler()
                    .withWizardNavigation(
                        scope = backgroundScope,
                        formController = formController,
                        formId = "mortgage",
                        onNext = { error("not expected") },
                        onFinish = { called = true },
                        onBack = { error("not expected") },
                    )

            handler.handle(FinishWizardAction("mortgage"))

            assertEquals(true, called)
        }

    @Test
    fun `actions for a different formId pass through untouched`() =
        runTest(UnconfinedTestDispatcher()) {
            val formController = FormController(schema)
            var handled: KompotAction? = null
            val handler =
                recordingActionHandler { handled = it }
                    .withWizardNavigation(
                        scope = backgroundScope,
                        formController = formController,
                        formId = "mortgage",
                        onNext = { error("not expected") },
                        onFinish = { error("not expected") },
                        onBack = { error("not expected") },
                    )

            handler.handle(NextStepAction("other_wizard"))

            assertEquals(NextStepAction("other_wizard"), handled)
        }
}
