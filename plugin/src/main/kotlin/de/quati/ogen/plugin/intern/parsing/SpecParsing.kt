package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecSecurityContext
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.model.Components
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.Paths
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation
import de.quati.ogen.plugin.intern.parsing.helper.longestCommonPrefix

/** The spec as code generation needs it, from the bundled and migrated OAS model. */
internal fun OpenApi.parse(sourceVersion: OasVersion, config: SpecConfig): Spec {
    // Both sections are optional; a spec without one parses as if it were empty.
    val rawComponents = components ?: Components()
    val rawPaths = paths ?: Paths()
    val version = when (sourceVersion) {
        OasVersion.V3_0 -> Spec.Version.V3_0
        OasVersion.V3_1 -> Spec.Version.V3_1
        OasVersion.V3_2 -> Spec.Version.V3_2
    }

    val securityRequirementObjects = rawComponents.securitySchemes?.parse() ?: emptyMap()
    val defaultSecurity = object : SpecSecurityContext {
        override val securityRequirementObjects = securityRequirementObjects
    }.run { security.parse() }

    val parserContext = ParserContext(
        version = version,
        defaultSecurity = defaultSecurity,
        securityRequirementObjects = securityRequirementObjects,
        operationIdGenerator = config.operationIdGenerator,
        tagPathPrefixes = rawPaths.getTagPathPrefixes(),
        mediaTypes = rawComponents.mediaTypes ?: emptyMap(),
        parameters = rawComponents.parameters ?: emptyMap(),
    )

    rawComponents.checkSchemaNameCollisions()
    val spec = with(parserContext) {
        rawComponents.schemas?.entries?.forEach { (rawName, obj) ->
            val name = ComponentName.Schema.Root.parse(rawName)
            val location = SchemaLocation.Root(
                type = SchemaLocation.Root.Type.COMPONENTS_SCHEMA,
                name = name,
            )
            val schema = obj.parse(location = location)
            // A component that only refers to another schema is not registered under its own name
            if (schema is Component.Schema.Ref && !isSchemaRegistered(name))
                addSchemaAlias(name = name, ref = schema)
        }
        val components = rawComponents.parse()
        val paths = rawPaths.parse()
        Spec(
            version = version,
            paths = paths,
            components = components,
            security = defaultSecurity,
            multipartBodySchemas = getMultipartBodySchemas(),
        )
    }
    return spec.copy(
        components = spec.components.copy(
            schemas = parserContext.getSchemas(),
            schemaAliases = parserContext.getSchemaAliases(),
        ),
    )
}

// References name a schema by its Kotlin name, so two schemas sharing one cannot be told apart
private fun Components.checkSchemaNameCollisions() {
    val collisions = schemas?.keys.orEmpty()
        .groupBy(ComponentName.Schema.Root::parse)
        .filterValues { it.size > 1 }
    if (collisions.isEmpty()) return
    error(collisions.entries.joinToString(separator = "\n") { (name, rawNames) ->
        "Schemas ${rawNames.joinToString { "'$it'" }} share the Kotlin name '$name'; rename all but one of them"
    })
}

context(_: ParserContext)
private fun Components.parse() = Spec.Components(
    schemas = emptyMap(), // ParserContext includes the actual schemas
    schemaAliases = emptyMap(), // ParserContext includes the actual schema aliases
    // swagger-parser dropped a parameter whose `in` is missing or none of the locations.
    parameters = parameters?.filterValues { it !is RefOr.Value || it.value.location != null }?.entries?.associate { (name, obj) ->
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

private fun Paths.getTagPathPrefixes() = pathItems.flatMap { (path, pathItem) ->
    pathItem.operations().map { (_, op) ->
        val tag = op.parseTag()
        tag to path
    }
}.groupingBy { it.first }.fold(
    initialValueSelector = { _, v -> v.second }
) { _, a, b -> longestCommonPrefix(a, b.second) }
