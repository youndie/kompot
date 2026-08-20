package io.github.youndie.kompot.form

// The contract for searching a remote data source — autocomplete, or any field with lookup.
// form-core does not know what comes back in the results: the concrete value type is defined in
// plug-in modules, as everywhere else where form-core works with the open FieldValue rather than a
// concrete type. Injected into FormController.
interface RemoteDataSourceResolver {
    suspend fun search(
        dataSourceId: String,
        query: String,
    ): List<FieldValue>
}
