package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.decode.OasDecoder
import de.quati.ogen.plugin.intern.oas.model.Header
import de.quati.ogen.plugin.intern.oas.model.MediaType
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.PathItem
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.oas.model.SchemaSerializer
import de.quati.ogen.plugin.intern.parsing.operations
import kotlinx.serialization.json.JsonElement

/**
 * The component schemas the paths and webhooks refer to, directly or through other schemas, parameters, bodies,
 * responses, headers, media types or callbacks, through every keyword that holds a schema. With [roots], also those
 * these schemas refer to. Path items that refer to others need no following: the bundler has already put the target
 * in their place.
 */
internal fun OpenApi.schemasReachedFromPaths(roots: Collection<Schema> = emptyList()): Set<String> {
    val all = components
    val reached = mutableSetOf<String>()
    val queue = ArrayDeque(roots)
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
    fun reach(name: String) {
        if (reached.add(name)) all?.schemas?.get(name)?.let(queue::add)
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
    fun pathItem(item: PathItem) {
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
            operation.callbacks?.values?.forEach { callback ->
                callback.follow("#/components/callbacks/", all?.callbacks)?.pathItems?.values?.forEach(::pathItem)
            }
        }
    }
    paths?.pathItems?.values?.forEach(::pathItem)
    webhooks?.values?.forEach(::pathItem)
    while (queue.isNotEmpty()) {
        val schema = queue.removeFirst() as? Schema.Obj ?: continue
        schema.ref?.takeIf { it.startsWith(SCHEMAS) }?.removePrefix(SCHEMAS)?.let(::reach)
        listOfNotNull(schema.items, schema.additionalProperties, schema.not).forEach(::schema)
        listOfNotNull(schema.prefixItems, schema.allOf, schema.anyOf, schema.oneOf).flatten().forEach(::schema)
        listOfNotNull(schema.properties, schema.patternProperties, schema.defs).flatMap { it.values }.forEach(::schema)
        listOfNotNull(
            schema.contains, schema.propertyNames, schema.ifSchema, schema.thenSchema, schema.elseSchema,
            schema.unevaluatedItems, schema.unevaluatedProperties,
        ).forEach(::schema)
        schema.dependentSchemas?.values?.forEach(::schema)
        UNMODELLED_SUBSCHEMA_KEYWORDS.forEach { keyword -> schema(schema.other[keyword]?.decodeSchema()) }
        schema.discriminator?.let { listOfNotNull(it.defaultMapping) + it.mapping?.values.orEmpty() }
            ?.mapNotNull { it.mappedSchemaName() }?.forEach(::reach)
    }
    return reached
}

private const val SCHEMAS = "#/components/schemas/"

// Keywords that hold a schema but have no field in [Schema.Obj], so they stay in [Schema.Obj.other].
private val UNMODELLED_SUBSCHEMA_KEYWORDS = listOf("contentSchema", "additionalItems")

private fun JsonElement.decodeSchema(): Schema? =
    runCatching { OasDecoder.json.decodeFromJsonElement(SchemaSerializer, this) }.getOrNull()

// A discriminator mapping value is a reference or, if it is no URI reference, the name of a component schema.
private fun String.mappedSchemaName(): String? = when {
    startsWith(SCHEMAS) -> removePrefix(SCHEMAS)
    '#' in this || '/' in this -> null
    else -> this
}
