package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import io.github.youndie.kompot.forms.AutocompleteInputComponent
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.RemoteDataSourceResolver
import io.github.youndie.kompot.form.standard.AutocompleteFieldDefinition
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.EqualsCondition
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeDataSourceResolver(
    private val resultsByQuery: Map<String, List<EntityValue>>,
) : RemoteDataSourceResolver {
    val searchedQueries = mutableListOf<Pair<String, String>>()

    override suspend fun search(
        dataSourceId: String,
        query: String,
    ): List<FieldValue> {
        searchedQueries += dataSourceId to query
        return resultsByQuery[query] ?: emptyList()
    }
}

@OptIn(ExperimentalTestApi::class)
class AutocompleteInputRendererTest {
    @Test
    fun `renders the label and the currently selected entity's title`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AutocompleteFieldDefinition("beneficiary", dataSourceId = "search"))),
                    initialValues = mapOf("beneficiary" to EntityValue(id = "b1", title = "Ada Lovelace")),
                )

            setContent {
                TestKompotTheme {
                    AutocompleteInputRenderer().Render(
                        component =
                            AutocompleteInputComponent(id = "c", fieldId = "beneficiary", label = "Recipient", dataSourceId = "search"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Recipient").assertIsDisplayed()
            onNodeWithText("Ada Lovelace").assertIsDisplayed()
        }

    @Test
    fun `typing a query searches the data source and lists results in the dropdown`() =
        runFormsComposeUiTest {
            val resolver =
                FakeDataSourceResolver(
                    resultsByQuery = mapOf("Ad" to listOf(EntityValue(id = "b1", title = "Ada Lovelace"))),
                )
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AutocompleteFieldDefinition("beneficiary", dataSourceId = "search"))),
                    dataSourceResolver = resolver,
                )

            setContent {
                TestKompotTheme {
                    AutocompleteInputRenderer().Render(
                        component =
                            AutocompleteInputComponent(id = "c", fieldId = "beneficiary", label = "Recipient", dataSourceId = "search"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Recipient").performTextInput("Ad")

            waitUntilExactlyOneExists(hasText("Ada Lovelace"), timeoutMillis = 5_000)
            assertEquals(listOf("search" to "Ad"), resolver.searchedQueries)
        }

    @Test
    fun `selecting a dropdown option stores it as the field's EntityValue`() =
        runFormsComposeUiTest {
            val resolver =
                FakeDataSourceResolver(
                    resultsByQuery = mapOf("Ad" to listOf(EntityValue(id = "b1", title = "Ada Lovelace"))),
                )
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AutocompleteFieldDefinition("beneficiary", dataSourceId = "search"))),
                    dataSourceResolver = resolver,
                )

            setContent {
                TestKompotTheme {
                    AutocompleteInputRenderer().Render(
                        component =
                            AutocompleteInputComponent(id = "c", fieldId = "beneficiary", label = "Recipient", dataSourceId = "search"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Recipient").performTextInput("Ad")
            waitUntilExactlyOneExists(hasText("Ada Lovelace"), timeoutMillis = 5_000)

            onNodeWithText("Ada Lovelace").performClick()
            waitForIdle()

            assertEquals(EntityValue(id = "b1", title = "Ada Lovelace"), controller.getTypedState<EntityValue>("beneficiary").value)
        }

    @Test
    fun `the field is not rendered at all when its visibleIf condition is not satisfied`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema(
                        "form",
                        fields =
                            listOf(
                                CheckboxFieldDefinition("gate"),
                                AutocompleteFieldDefinition(
                                    "beneficiary",
                                    dataSourceId = "search",
                                    visibleIf = EqualsCondition("gate", BooleanValue(true)),
                                ),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    AutocompleteInputRenderer().Render(
                        component =
                            AutocompleteInputComponent(id = "c", fieldId = "beneficiary", label = "Recipient", dataSourceId = "search"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onAllNodesWithText("Recipient").assertCountEquals(0)
        }
}
