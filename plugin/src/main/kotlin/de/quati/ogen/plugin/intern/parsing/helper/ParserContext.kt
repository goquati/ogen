package de.quati.ogen.plugin.intern.parsing.helper

import de.quati.ogen.plugin.EndpointInfo
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.OperationName
import de.quati.ogen.plugin.intern.model.Security
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecInfoContext
import de.quati.ogen.plugin.intern.model.Tag
import de.quati.ogen.plugin.intern.model.HttpMethod
import de.quati.ogen.plugin.intern.oas.model.MediaType
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.RefOr

internal class ParserContext(
    override val version: Spec.Version,
    override val defaultSecurity: Security,
    override val securityRequirementObjects: Map<String, Security.RequirementObject>,
    val operationIdGenerator: ((EndpointInfo) -> String?)?,
    val tagPathPrefixes: Map<Tag, String>,
    /** `components/mediaTypes` (3.2), for references to media types. */
    private val mediaTypes: Map<String, RefOr<MediaType>> = emptyMap(),
    /** `components/parameters`, as swagger-parser put referenced parameters of operations in place. */
    private val parameters: Map<String, RefOr<Parameter>> = emptyMap(),
    /** The component schemas the paths refer to, directly or through other schemas. */
    private val schemasReachedFromPaths: Set<String> = emptySet(),
) : SpecInfoContext {
    /**
     * Whether the schema being parsed is a component schema that swagger-parser's 3.1 dereferencer reached through
     * a `$ref` from the paths; in those it turned `additionalProperties: true` into a Boolean. Inline schemas of
     * the paths kept the boolean schema.
     */
    var reachedFromPaths: Boolean = false
        private set

    fun <T> inComponentSchema(name: String, block: () -> T): T {
        val before = reachedFromPaths
        reachedFromPaths = name in schemasReachedFromPaths
        try {
            return block()
        } finally {
            reachedFromPaths = before
        }
    }

    /** The parameter a reference to `components/parameters` names, following references; null if there is none. */
    fun resolveParameter(ref: String): Parameter? {
        val seen = mutableSetOf<String>()
        var current = ref
        while (true) {
            val name = current.removePrefix(PARAMETERS).takeIf { current.startsWith(PARAMETERS) && seen.add(it) } ?: return null
            current = when (val parameter = parameters[name] ?: return null) {
                is RefOr.Value -> return parameter.value
                is RefOr.Ref -> parameter.ref
            }
        }
    }

    /** The media type itself; a reference is followed to `components/mediaTypes`. */
    fun resolveMediaType(mediaType: RefOr<MediaType>): MediaType {
        val seen = mutableSetOf<String>()
        var current = mediaType
        while (true) {
            current = when (current) {
                is RefOr.Value -> return current.value
                is RefOr.Ref -> {
                    val name = current.ref.removePrefix("#/components/mediaTypes/")
                        .takeIf { current.ref.startsWith("#/components/mediaTypes/") && seen.add(it) }
                        ?: error("Media type reference '${current.ref}' cannot be resolved")
                    mediaTypes[name] ?: error("Media type reference '${current.ref}' cannot be resolved")
                }
            }
        }
    }

    private val schemas = mutableMapOf<ComponentName.Schema, Component.Schema.NonInline>()
    private val reservedNames = mutableSetOf<ComponentName.Schema>()
    private val generatedOperationIds = mutableSetOf<GeneratedOperationId>()
    private val multipartBodySchemas = mutableSetOf<ComponentName.Schema>()
    private val schemaAliases = mutableMapOf<ComponentName.Schema, Component.Schema.Ref>()

    private data class GeneratedOperationId(
        val id: OperationName,
        val tag: Tag,
    )

    fun getSchemas() = schemas.toMap()
    fun getMultipartBodySchemas() = multipartBodySchemas.toSet()
    fun markMultipartBodySchema(name: ComponentName.Schema) {
        multipartBodySchemas += name
    }
    fun getSchemaAliases() = schemaAliases.toMap()
    fun addSchemaAlias(name: ComponentName.Schema, ref: Component.Schema.Ref) {
        schemaAliases[name] = ref
    }
    fun isSchemaRegistered(name: ComponentName.Schema) = name in schemas
    fun registerSchema(
        location: SchemaLocation,
        schemaGen: (SchemaLocation) -> Component.Schema,
    ): Component.Schema.Inline {
        val blacklist = schemas.keys + reservedNames // TODO + setOf("Serializer")
        val location = when (location) {
            is SchemaLocation.Root -> location.copy(name = location.name.makeDifferent(blacklist))
            is SchemaLocation.Nested -> SchemaLocation.Nested(location.name.makeDifferent(blacklist))
        }
        reservedNames += location.name
        val schema = when (val schema = schemaGen(location)) {
            is Component.Schema.Inline -> return schema
            is Component.Schema.NonInline -> schema
        }
        schemas[location.name] = schema.copySchema(newName = location.name)
        reservedNames -= location.name
        return Component.Schema.Ref.create(
            name = location.name,
            isNullable = schema.isNullable,
        )
    }

    fun generateOperationId(
        path: String,
        method: HttpMethod,
        tag: Tag,
    ): OperationName? {
        if (operationIdGenerator == null) return null
        val name = operationIdGenerator(
            EndpointInfo(
                tagPathPrefix = tagPathPrefixes[tag] ?: "",
                path = path,
                method = EndpointInfo.Method.of(method),
                tag = tag.name,
            )
        ) ?: return null
        return OperationName.parse(name)
    }

    private companion object {
        const val PARAMETERS = "#/components/parameters/"
    }

    fun checkOperationId(id: OperationName, tag: Tag) {
        val obj = GeneratedOperationId(id = id, tag = tag)
        if (obj in generatedOperationIds) error("Operation ID '$id' in tag '$tag' already exists")
        generatedOperationIds += obj
    }
}
