package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecSecurityContext
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation
import de.quati.ogen.plugin.intern.parsing.helper.longestCommonPrefix
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.parser.core.models.SwaggerParseResult

internal fun SwaggerParseResult.parse(config: SpecConfig): Spec {
    val raw = openAPI!!
    val version = if (isOpenapi31)
        Spec.Version.V3_1
    else
        Spec.Version.V3_0

    val securityRequirementObjects = raw.components.securitySchemes?.parse() ?: emptyMap()
    val defaultSecurity = object : SpecSecurityContext {
        override val securityRequirementObjects = securityRequirementObjects
    }.run { raw.security.parse() }

    val parserContext = ParserContext(
        version = version,
        defaultSecurity = defaultSecurity,
        securityRequirementObjects = securityRequirementObjects,
        operationIdGenerator = config.operationIdGenerator,
        tagPathPrefixes = raw.paths.getTagPathPrefixes(),
    )

    val spec = with(parserContext) {
        raw.components.schemas?.entries?.forEach { (name, obj) ->
            val name = ComponentName.Schema.Root.parse(name)
            val location = SchemaLocation.Root(
                type = SchemaLocation.Root.Type.COMPONENTS_SCHEMA,
                name = name,
            )
            obj.parse(location = location)
        }
        val components = raw.components.parse()
        val paths = raw.paths.parse()
        Spec(
            version = version,
            paths = paths,
            components = components,
            security = defaultSecurity,
        )
    }
    return spec.copy(
        components = spec.components.copy(
            schemas = parserContext.getSchemas(),
        ),
    )
}

context(_: ParserContext)
private fun io.swagger.v3.oas.models.Components.parse() = Spec.Components(
    schemas = emptyMap(), // ParserContext includes the actual schemas
    parameters = parameters?.entries?.associate { (name, obj) ->
        val name = ComponentName.Parameter.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    response = responses?.entries?.associate { (name, obj) ->
        val name = ComponentName.Response.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    requestBody = requestBodies?.entries?.associate { (name, obj) ->
        val name = ComponentName.RequestBody.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
)

private fun Paths.getTagPathPrefixes() = flatMap { (path, pathItem) ->
    pathItem.readOperations().map { op ->
        val tag = op.parseTag()
        tag to path!!
    }
}.groupingBy { it.first }.fold(
    initialValueSelector = { _, v -> v.second }
) { _, a, b -> longestCommonPrefix(a, b.second) }
