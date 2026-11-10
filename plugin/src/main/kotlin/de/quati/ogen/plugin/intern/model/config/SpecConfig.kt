package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.EndpointInfo
import de.quati.ogen.plugin.intern.buildMergedSpec
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.GlobalGenContext
import de.quati.ogen.plugin.intern.parsing.parse
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions

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

    val parseResult by lazy { parse(inferSchemaType = true) }

    /**
     * The spec with only the types it declares: in 3.0, swagger-parser otherwise infers a `type` from `items`,
     * `additionalProperties` or an enum's first value, even over a declared one. Checks that compare types read this.
     */
    val declaredTypesSpec: OpenAPI? by lazy { parse(inferSchemaType = false).openAPI }

    private fun parse(inferSchemaType: Boolean) = OpenAPIParser().readLocation(
        path.toString(),
        null,
        ParseOptions().apply {
            isResolve = true
            isInferSchemaType = inferSchemaType
        },
    )!!

    context(c: GlobalGenContext)
    fun toCodeGenContext() = CodeGenContext(
        specConfig = this,
        spec = parseResult.parse(config = this),
        globalGenContext = c,
    )
}
