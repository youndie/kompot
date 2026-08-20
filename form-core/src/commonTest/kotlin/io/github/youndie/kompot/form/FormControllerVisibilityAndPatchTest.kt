package io.github.youndie.kompot.form

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class FakeValue(
    val value: String,
) : FieldValue

private data class FakeRequiredRule(
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(value: FieldValue?, getFieldValue: (fieldId: String) -> FieldValue?): Boolean = value is FakeValue && value.value.isNotBlank()
}

private data class FakeEqualsCondition(
    val fieldId: String,
    val expectedValue: FieldValue,
) : FormCondition {
    override fun evaluate(getFieldValue: (fieldId: String) -> FieldValue?): Boolean = getFieldValue(fieldId) == expectedValue
}

private data class FakeFieldDefinition(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
    override val visibleIf: FormCondition? = null,
    override val triggersPatch: Boolean = false,
) : FormFieldDefinition

class FormControllerVisibilityTest {
    // The auto-numbering checkbox is always visible; the document number only while auto-numbering
    // is off.
    private fun schemaWithConditionalField() =
        FormSchema(
            formId = "test",
            fields =
                listOf(
                    FakeFieldDefinition("auto_numbering"),
                    FakeFieldDefinition(
                        "doc_number",
                        rules = listOf(FakeRequiredRule("required")),
                        visibleIf = FakeEqualsCondition("auto_numbering", FakeValue("off")),
                    ),
                ),
        )

    @Test
    fun `field without visibleIf is always visible`() {
        val controller = FormController(FormSchema("test", listOf(FakeFieldDefinition("name"))))

        assertTrue(controller.isFieldVisible("name"))
    }

    @Test
    fun `field is hidden when its condition is not satisfied`() {
        val controller = FormController(schemaWithConditionalField())

        // auto_numbering is not yet "off" — it has no value at all — so doc_number is hidden
        assertFalse(controller.isFieldVisible("doc_number"))
    }

    @Test
    fun `field becomes visible once the referenced field satisfies the condition`() {
        val controller = FormController(schemaWithConditionalField())

        controller.onValueChanged("auto_numbering", FakeValue("off"))

        assertTrue(controller.isFieldVisible("doc_number"))
    }

    @Test
    fun `hidden field is excluded from payload even if it has a value`() {
        val controller = FormController(schemaWithConditionalField())

        // The user managed to type a value while the field was visible...
        controller.onValueChanged("auto_numbering", FakeValue("off"))
        controller.onValueChanged("doc_number", FakeValue("151024-2231"))
        // ...and then hid the field again
        controller.onValueChanged("auto_numbering", FakeValue("on"))

        val payload = controller.getPayload()

        assertNotNull(payload)
        assertFalse("doc_number" in payload)
    }

    @Test
    fun `hidden required field does not block submit`() {
        val controller = FormController(schemaWithConditionalField())
        // doc_number is hidden and was never filled in, so `required` must not block submission

        controller.markAllAsChanged()

        assertNull(controller.getTypedState<FakeValue>("doc_number").error)
        assertTrue(controller.getPayload() != null)
    }

    @Test
    fun `visible required field still blocks submit when empty`() {
        val controller = FormController(schemaWithConditionalField())
        controller.onValueChanged("auto_numbering", FakeValue("off")) // doc_number now visible and required

        controller.markAllAsChanged()

        assertEquals("required", controller.getTypedState<FakeValue>("doc_number").error)
        assertNull(controller.getPayload())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FormControllerPatchTest {
    // The controller's scope must share the test's virtual-time scheduler — otherwise
    // advanceUntilIdle() will not advance its forever-running mapLatest collector — but it must NOT
    // be a structural child of the TestScope: that collector never completes on its own, and runTest
    // would fail with "uncompleted coroutines" if it were a child job of the test. backgroundScope,
    // contrary to expectation, does not help either: its child coroutines are not advanced by
    // advanceUntilIdle() in this version of kotlinx-coroutines-test — verified experimentally. A
    // separate SupervisorJob solves both problems at once.
    private fun TestScope.controllerScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun schema() =
        FormSchema(
            formId = "test",
            fields =
                listOf(
                    FakeFieldDefinition("template_type", triggersPatch = true),
                    FakeFieldDefinition("bic"),
                    FakeFieldDefinition("comment"),
                ),
        )

    @Test
    fun `applyPatch updates values and clears fields`() {
        val controller = FormController(schema())
        controller.onValueChanged("comment", FakeValue("draft"))

        controller.applyPatch(
            FormPatch(
                updates = mapOf("bic" to FakeValue("SWIFT123")),
                clearFields = listOf("comment"),
            ),
        )

        assertEquals(FakeValue("SWIFT123"), controller.getTypedState<FakeValue>("bic").value)
        assertNull(controller.getTypedState<FakeValue>("comment").value)
    }

    @Test
    fun `applyPatch emits a focus request`() =
        runBlocking {
            val controller = FormController(schema())

            val focusDeferred = async { controller.focusRequests.first() }
            yield() // let the collector subscribe before the emit

            controller.applyPatch(FormPatch(focusOn = "amount"))

            assertEquals("amount", focusDeferred.await())
        }

    // requestPatchIfNeeded no longer suspends: the controller runs the background request itself
    // through mapLatest in its own scope (see FormController.kt). The test therefore needs
    // controllerScope() above plus advanceUntilIdle() to wait deterministically for that inner
    // coroutine to run before asserting.
    @Test
    fun `requestPatchIfNeeded fetches and applies a patch — toggling isLoading`() =
        runTest {
            var fetcherCalledWith: Pair<String, Map<String, FieldValue>>? = null
            val scope = controllerScope()
            val controller =
                FormController(
                    schema(),
                    patchFetcher = { fieldId, payload ->
                        fetcherCalledWith = fieldId to payload
                        FormPatch(updates = mapOf("bic" to FakeValue("SWIFT123")))
                    },
                    scope = scope,
                )

            controller.onValueChanged("template_type", FakeValue("SWIFT"))
            assertFalse(controller.isLoading.value) // no patch requested yet

            controller.requestPatchIfNeeded("template_type")
            advanceUntilIdle()

            assertEquals("template_type", fetcherCalledWith?.first)
            assertEquals(FakeValue("SWIFT"), fetcherCalledWith?.second?.get("template_type"))
            assertEquals(FakeValue("SWIFT123"), controller.getTypedState<FakeValue>("bic").value)
            assertFalse(controller.isLoading.value) // the patch was applied, loading is done
            scope.cancel()
        }

    @Test
    fun `requestPatchIfNeeded is a no-op for a field without triggersPatch`() =
        runTest {
            var fetcherCalled = false
            val scope = controllerScope()
            val controller =
                FormController(schema(), patchFetcher = { _, _ -> fetcherCalled = true; FormPatch() }, scope = scope)

            controller.requestPatchIfNeeded("bic") // bic has triggersPatch = false
            advanceUntilIdle()

            assertFalse(fetcherCalled)
            scope.cancel()
        }

    @Test
    fun `requestPatchIfNeeded is a no-op when no patchFetcher is configured`() =
        runTest {
            val scope = controllerScope()
            val controller = FormController(schema(), scope = scope) // no patchFetcher

            // must not throw
            controller.requestPatchIfNeeded("template_type")
            advanceUntilIdle()

            assertFalse(controller.isLoading.value)
            scope.cancel()
        }

    // The race: two quick triggers in a row on the same field — the patchFetcher tells them apart by
    // call number, not by fieldId. mapLatest must cancel the first, still-unfinished request, and
    // only the patch from the second, freshest call may be applied.
    @Test
    fun `a second trigger before the first patch resolves cancels the first — only the latest patch applies`() =
        runTest {
            var callCount = 0
            val scope = controllerScope()
            val controller =
                FormController(
                    schema(),
                    patchFetcher = { _, _ ->
                        callCount++
                        if (callCount == 1) {
                            delay(100) // the first, slow request — it must be cancelled
                            FormPatch(updates = mapOf("bic" to FakeValue("FIRST-STALE")))
                        } else {
                            FormPatch(updates = mapOf("bic" to FakeValue("SECOND-FRESH")))
                        }
                    },
                    scope = scope,
                )

            controller.requestPatchIfNeeded("template_type")
            // runCurrent() lets the collector actually START the first performPatch and reach
            // delay(100) and suspend. Without this step both tryEmit calls coalesce into the single
            // replay slot before the collector begins collecting at all, and mapLatest sees ONE value
            // rather than "a second trigger on top of a still-running first".
            runCurrent()
            controller.requestPatchIfNeeded("template_type")
            advanceUntilIdle()

            // Without cancellation the second, fast answer would apply first and then be overwritten
            // by the slower first request. The outcome must come from the LAST trigger.
            assertEquals(FakeValue("SECOND-FRESH"), controller.getTypedState<FakeValue>("bic").value)
            scope.cancel()
        }

    // The try/catch in performPatch must not take the application down on a network failure: the
    // form stays alive and the error appears under the field that triggered the patch rather than
    // propagating out.
    @Test
    fun `a failed patch request sets a field error instead of crashing`() =
        runTest {
            val scope = controllerScope()
            val controller =
                FormController(
                    schema(),
                    patchFetcher = { _, _ -> error("network is down") },
                    scope = scope,
                )

            controller.requestPatchIfNeeded("template_type")
            advanceUntilIdle()

            assertFalse(controller.isLoading.value)
            assertEquals(
                "Failed to load data: network is down",
                controller.getTypedState<FakeValue>("template_type").error,
            )
            scope.cancel()
        }
}
