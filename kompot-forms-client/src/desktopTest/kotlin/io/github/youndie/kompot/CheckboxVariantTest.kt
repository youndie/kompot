package io.github.youndie.kompot

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.forms.CheckboxInputComponent
import io.github.youndie.kompot.forms.KompotCheckboxVariants
import kotlin.test.Test

// A switch and a checkbox make different promises on both phones: a switch takes effect now, a
// checkbox takes effect on submit. The wire could not say which, so a settings screen either promised
// the wrong thing or left the toolkit's form machinery for a control that needs none of it.
@OptIn(ExperimentalTestApi::class)
class CheckboxVariantTest {
    private fun controller() =
        FormController(FormSchema(formId = "f", fields = listOf(CheckboxFieldDefinition(fieldId = "roaming"))))

    private fun component(variant: String?) =
        CheckboxInputComponent(id = "c", fieldId = "roaming", label = "Roaming", variant = variant)

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `the switch variant draws a switch`() =
        runFormsComposeUiTest {
            setContent {
                TestKompotTheme {
                    CheckboxInputRenderer().Render(component(KompotCheckboxVariants.SWITCH), recordingActionHandler(), controller())
                }
            }

            onNode(hasRole(Role.Switch)).assertIsOff()
        }

    @Test
    fun `no variant draws a checkbox, exactly as before`() =
        runFormsComposeUiTest {
            setContent {
                TestKompotTheme {
                    CheckboxInputRenderer().Render(component(variant = null), recordingActionHandler(), controller())
                }
            }

            onNode(hasRole(Role.Checkbox)).assertIsOff()
        }

    // An unfamiliar word is not a failure: the same degradation an unknown component gets, one field
    // down. A client released before a variant existed draws what it always drew.
    @Test
    fun `a variant this client does not know degrades to a checkbox`() =
        runFormsComposeUiTest {
            setContent {
                TestKompotTheme {
                    CheckboxInputRenderer().Render(component(variant = "segmented"), recordingActionHandler(), controller())
                }
            }

            onNode(hasRole(Role.Checkbox)).assertIsOff()
        }

    // The affordance changed and the state did not: a switch writes the same BooleanValue through the
    // same controller, which is the half that must not have moved.
    @Test
    fun `a switch writes the field the way a checkbox does`() =
        runFormsComposeUiTest {
            val controller = controller()
            setContent {
                TestKompotTheme {
                    CheckboxInputRenderer().Render(component(KompotCheckboxVariants.SWITCH), recordingActionHandler(), controller)
                }
            }

            onNode(hasRole(Role.Switch)).performClick()
            waitForIdle()

            onNode(hasRole(Role.Switch)).assertIsOn()
            kotlin.test.assertEquals(BooleanValue(true), controller.fieldsState.value["roaming"]?.value)
        }
}
