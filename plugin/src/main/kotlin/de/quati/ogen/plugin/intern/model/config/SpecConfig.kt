package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.EndpointInfo
import de.quati.ogen.plugin.intern.buildMergedSpec
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.GlobalGenContext
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.oas.OasParseResult
import de.quati.ogen.plugin.intern.oas.OasParser
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.parsing.parse
import org.gradle.api.GradleException

internal data class SpecConfig(
    val inputConfig: InputConfig,
    val modelConfig: GeneratorConfig.Model,
    val generatorConfigs: List<GeneratorConfig>,
    val validatorConfig: ValidatorConfig?,
    val operationIdGenerator: ((EndpointInfo) -> String?)?,
) {
    private val path by lazy {
        when (inputConfig) {
            is InputConfig.File -> inputConfig.path
            is InputConfig.Merge -> inputConfig.buildMergedSpec()
        }
    }

    /** The spec as the own parser reads it: loaded with what it refers to, migrated to 3.2, bundled, checked. */
    val oasParseResult: OasParseResult by lazy {
        OasParser.parse(path, ResourceLoader.default())
    }

    context(c: GlobalGenContext)
    fun toCodeGenContext() = CodeGenContext(
        specConfig = this,
        spec = toSpec(),
        globalGenContext = c,
    )

    /**
     * The spec code is generated from. As before with swagger-parser, problems in the spec do not stop generation
     * (the validator reports them); only a spec that cannot be read at all does.
     */
    fun toSpec(): Spec {
        val result = oasParseResult
        val api = result.openApi ?: throw GradleException(
            "The spec '$inputConfig' cannot be read:\n" + result.errors.joinToString("\n") { "\t$it" }
        )
        return api.parse(result.sourceVersion!!, this)
    }
}
