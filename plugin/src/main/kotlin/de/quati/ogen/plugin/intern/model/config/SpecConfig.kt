package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.intern.buildMergedSpec
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.GlobalGenContext
import de.quati.ogen.plugin.intern.model.parse
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions

internal data class SpecConfig(
    val inputConfig: InputConfig,
    val modelConfig: GeneratorConfig.Model,
    val generatorConfigs: List<GeneratorConfig>,
    val validatorConfig: ValidatorConfig?,
) {
    val parseResult by lazy {
        val path = when (inputConfig) {
            is InputConfig.File -> inputConfig.path
            is InputConfig.Merge -> inputConfig.buildMergedSpec()
        }
        OpenAPIParser().readLocation(
            path.toString(),
            null,
            ParseOptions().apply {
                isResolve = true
            },
        )!!
    }

    context(c: GlobalGenContext)
    fun toCodeGenContext() = CodeGenContext(
        specConfig = this,
        spec = parseResult.parse(),
        globalGenContext = c,
    )
}
