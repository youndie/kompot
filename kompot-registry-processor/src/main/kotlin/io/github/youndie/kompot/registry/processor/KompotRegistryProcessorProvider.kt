package io.github.youndie.kompot.registry.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class KompotRegistryProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KompotRegistrySymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            // Unique per module (see `ksp { arg("kompotModuleTag", "...") }` in each consuming
            // module's build.gradle.kts): otherwise Generated<Tag>KompotRegistration.kt from two
            // different modules would collide on file and object name inside one package.
            moduleTag = environment.options["kompotModuleTag"] ?: error("kompotModuleTag KSP option is required"),
        )
}
