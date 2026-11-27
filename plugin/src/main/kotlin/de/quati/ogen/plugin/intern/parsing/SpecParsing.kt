package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecSecurityContext
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.model.Components
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.model.Header
import de.quati.ogen.plugin.intern.oas.model.MediaType
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
        schemasReachedFromPaths = schemasReachedFromPaths(),
    )

    rawComponents.checkSchemaNameCollisions()
    val spec = with(parserContext) {
        rawComponents.schemas?.entries?.forEach { (rawName, obj) ->
            val name = ComponentName.Schema.Root.parse(rawName)
            val location = SchemaLocation.Root(
                type = SchemaLocation.Root.Type.COMPONENTS_SCHEMA,
                name = name,
            )
            val schema = inComponentSchema(rawName) { obj.parse(location = location) }
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

/**
 * The component schemas the paths refer to, directly or through other schemas, parameters, bodies, responses,
 * headers or media types: those swagger-parser's 3.1 dereferencer walked.
 */
internal fun OpenApi.schemasReachedFromPaths(): Set<String> {
    val all = components
    val reached = mutableSetOf<String>()
    val queue = ArrayDeque<Schema>()
    val seenComponents = mutableSetOf<String>()
    fun <T> RefOr<T>.follow(prefix: String, map: Map<String, RefOr<T>>?): T? {
        var current = this
        while (true) {
            current = when (current) {
                is RefOr.Value -> return current.value
                is RefOr.Ref -> {
                    if (!current.ref.startsWith(prefix) || !seenComponents.add(current.ref)) return null
                    map?.get(current.ref.removePrefix(prefix)) ?: return null
                }
            }
        }
    }
    fun schema(schema: Schema?) {
        if (schema != null) queue += schema
    }
    fun media(media: RefOr<MediaType>) = media.follow("#/components/mediaTypes/", all?.mediaTypes)?.let {
        schema(it.schema)
        schema(it.itemSchema)
    }
    fun content(content: Map<String, RefOr<MediaType>>?) = content?.values?.forEach(::media)
    fun header(header: RefOr<Header>) = header.follow("#/components/headers/", all?.headers)?.let {
        schema(it.schema)
        content(it.content)
    }
    fun parameter(parameter: RefOr<Parameter>) = parameter.follow("#/components/parameters/", all?.parameters)?.let {
        schema(it.schema)
        content(it.content)
    }
    paths?.pathItems?.values?.forEach { item ->
        item.parameters?.forEach(::parameter)
        item.operations().forEach { (_, operation) ->
            operation.parameters?.forEach(::parameter)
            operation.requestBody?.follow("#/components/requestBodies/", all?.requestBodies)?.let { content(it.content) }
            operation.responses?.responses?.values?.forEach { response ->
                response.follow("#/components/responses/", all?.responses)?.let {
                    content(it.content)
                    it.headers?.values?.forEach(::header)
                }
            }
        }
    }
    while (queue.isNotEmpty()) {
        val schema = queue.removeFirst() as? Schema.Obj ?: continue
        schema.ref?.takeIf { it.startsWith(SCHEMAS) }?.removePrefix(SCHEMAS)?.let { name ->
            if (reached.add(name)) all?.schemas?.get(name)?.let(queue::add)
        }
        listOfNotNull(schema.items, schema.additionalProperties, schema.not).forEach(::schema)
        listOfNotNull(schema.prefixItems, schema.allOf, schema.anyOf, schema.oneOf).flatten().forEach(::schema)
        listOfNotNull(schema.properties, schema.patternProperties, schema.defs).flatMap { it.values }.forEach(::schema)
    }
    return reached
}

private const val SCHEMAS = "#/components/schemas/"

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
