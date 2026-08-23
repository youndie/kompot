package io.github.youndie.kompot.form

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class SoValue(
    val id: String,
    val title: String,
) : FieldValue

private data class SoFieldDefinition(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
) : FormFieldDefinition

private fun schema() = FormSchema("test", listOf(SoFieldDefinition("beneficiary")))

class FormControllerSearchOptionsTest {
    @Test
    fun `searchOptions delegates to the configured resolver`() =
        runTest {
            var calledWith: Pair<String, String>? = null
            val resolver =
                object : RemoteDataSourceResolver {
                    override suspend fun search(
                        dataSourceId: String,
                        query: String,
                    ): List<FieldValue> {
                        calledWith = dataSourceId to query
                        return listOf(SoValue("1", "Ivan Petrov"))
                    }
                }
            val controller = FormController(schema(), dataSourceResolver = resolver)

            val results = controller.searchOptions("beneficiaries_search", "Ivan")

            assertEquals("beneficiaries_search" to "Ivan", calledWith)
            assertEquals(listOf(SoValue("1", "Ivan Petrov")), results)
        }

    @Test
    fun `searchOptions returns an empty list when no resolver is configured`() =
        runTest {
            val controller = FormController(schema()) // no dataSourceResolver

            val results = controller.searchOptions("beneficiaries_search", "Ivan")

            assertTrue(results.isEmpty())
        }
}
