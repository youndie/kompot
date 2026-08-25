package io.github.youndie.kompot

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.github.youndie.kompot.forms.SubmitFormAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun loginSubmitTestSchema() =
    FormSchema(
        formId = "login",
        fields = listOf(TestField(fieldId = "password", rules = listOf(TestRequiredRule("required")))),
    )

private data class LoginSubmitTestResultAction(
    val token: String,
) : KompotAction

@OptIn(ExperimentalCoroutinesApi::class)
class LoginSubmitTest {
    @Test
    fun `submitting a valid form calls submit with the payload and feeds the result back into the chain`() =
        runTest {
            val controller = FormController(loginSubmitTestSchema())
            controller.onValueChanged("password", TestValue("1234"))
            var forwarded: KompotAction? = null
            var submitCalledWith: Map<String, FieldValue>? = null
            val handler =
                KompotActionHandler { forwarded = it }
                    .withLoginSubmit(this, controller, formId = "login") { payload ->
                        submitCalledWith = payload
                        LoginSubmitTestResultAction("fresh-token")
                    }

            handler.handle(SubmitFormAction(formId = "login"))
            advanceUntilIdle()

            assertEquals(mapOf("password" to TestValue("1234")), submitCalledWith)
            assertEquals(LoginSubmitTestResultAction("fresh-token"), forwarded)
        }

    @Test
    fun `submitting an invalid form does not call submit at all`() =
        runTest {
            val controller = FormController(loginSubmitTestSchema()) // password left empty -> required rule fails
            var submitCalled = false
            val handler =
                KompotActionHandler {}
                    .withLoginSubmit(this, controller, formId = "login") { submitCalled = true; LoginSubmitTestResultAction("x") }

            handler.handle(SubmitFormAction(formId = "login"))
            advanceUntilIdle()

            assertEquals(false, submitCalled)
        }

    @Test
    fun `the original SubmitFormAction is forwarded synchronously even before submit resolves`() =
        runTest {
            val controller = FormController(loginSubmitTestSchema())
            controller.onValueChanged("password", TestValue("1234"))
            var forwarded: KompotAction? = null
            val handler =
                KompotActionHandler { forwarded = it }
                    .withLoginSubmit(this, controller, formId = "login") { LoginSubmitTestResultAction("x") }

            handler.handle(SubmitFormAction(formId = "login"))

                // Only the original submit action has arrived before advanceUntilIdle(): the
                // asynchronous submit has not run yet.
            assertEquals(SubmitFormAction(formId = "login"), forwarded)
        }

    @Test
    fun `a SubmitFormAction for a different formId is ignored`() =
        runTest {
            val controller = FormController(loginSubmitTestSchema())
            controller.onValueChanged("password", TestValue("1234"))
            var submitCalled = false
            var forwarded: KompotAction? = null
            val handler =
                KompotActionHandler { forwarded = it }
                    .withLoginSubmit(this, controller, formId = "login") { submitCalled = true; LoginSubmitTestResultAction("x") }

            handler.handle(SubmitFormAction(formId = "other_form"))
            advanceUntilIdle()

            assertEquals(false, submitCalled)
            assertEquals(SubmitFormAction(formId = "other_form"), forwarded)
        }

    @Test
    fun `non-submit actions pass through untouched`() =
        runTest {
            val controller = FormController(loginSubmitTestSchema())
            var forwarded: KompotAction? = null
            val handler =
                KompotActionHandler { forwarded = it }
                    .withLoginSubmit(this, controller, formId = "login") { LoginSubmitTestResultAction("x") }

            handler.handle(NavigateAction(deeplink = "/home"))

            assertEquals(NavigateAction(deeplink = "/home"), forwarded)
        }

    @Test
    fun `sanity - getPayload is null once required fields are validated and still empty`() {
        val controller = FormController(loginSubmitTestSchema())
            // getPayload() looks only at state.error, which is set on blur or by markAllAsChanged; on
            // an untouched controller it is null, which does not mean "valid" — hence forcing first.
        controller.markAllAsChanged()
        assertNull(controller.getPayload())
    }
}
