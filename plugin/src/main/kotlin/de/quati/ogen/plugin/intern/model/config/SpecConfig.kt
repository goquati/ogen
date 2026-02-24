package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.model.parse
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import kotlin.io.path.Path

internal data class SpecConfig(
    val inputConfig: InputConfig,
    val modelConfig: GeneratorConfig.Model,
    val sharedConfig: GeneratorConfig.Shared,
    val generatorConfigs: List<GeneratorConfig>,
    val validatorConfig: ValidatorConfig?,
) {
    val parseResult by lazy {
        val path = when (inputConfig) {
            is InputConfig.File -> inputConfig.path
            is InputConfig.Directory -> inputConfig.toMergedSpecBuilder().buildMergedSpec().let(::Path)
        }
        OpenAPIParser().readLocation(
            path.toString(),
            null,
            ParseOptions().apply {
                isResolve = true
            },
        )!!
    }

    fun toCodeGenContext() = CodeGenContext(
        specConfig = this,
        spec = parseResult.parse(),
    )
}
