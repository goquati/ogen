package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.model.parse
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import java.nio.file.Path

internal data class SpecConfig(
    val apiFile: Path,
    val modelConfig: GeneratorConfig.Model,
    val sharedConfig: GeneratorConfig.Shared,
    val generatorConfigs: List<GeneratorConfig>,
    val validatorConfig: ValidatorConfig?,
) {
    val parseResult by lazy {
        OpenAPIParser().readLocation(
            apiFile.toString(),
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
