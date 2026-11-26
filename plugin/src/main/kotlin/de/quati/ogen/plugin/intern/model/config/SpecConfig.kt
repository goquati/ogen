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
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions
import org.gradle.api.GradleException

internal data class SpecConfig(
    val inputConfig: InputConfig,
    val modelConfig: GeneratorConfig.Model,
    val generatorConfigs: List<GeneratorConfig>,
    val validatorConfig: ValidatorConfig?,
    val operationIdGenerator: ((EndpointInfo) -> String?)?,
    /** URI prefixes of remote documents that references may load; none by default. */
    val remoteRefAllowlist: List<String> = emptyList(),
) {
    private val path by lazy {
        when (inputConfig) {
            is InputConfig.File -> inputConfig.path
            is InputConfig.Merge -> inputConfig.buildMergedSpec()
        }
    }

    /** The spec as the own parser reads it: loaded with what it refers to, migrated to 3.2, bundled, checked. */
    val oasParseResult: OasParseResult by lazy {
        OasParser.parse(path, ResourceLoader.default(httpAllowlist = remoteRefAllowlist))
    }

    /** The spec as swagger-parser reads it; only the validator still uses it (plan, phase 7). */
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
        spec = toSpec(),
        globalGenContext = c,
    )

    /** The spec code is generated from; a spec with errors cannot be generated from. */
    fun toSpec(): Spec {
        val result = oasParseResult
        val api = result.openApi ?: throw GradleException(
            "The spec '$inputConfig' cannot be read:\n" + result.errors.joinToString("\n") { "\t$it" }
        )
        return api.parse(result.sourceVersion!!, this)
    }
}
