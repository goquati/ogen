package de.quati.ogen.plugin.intern.parsing.helper

import de.quati.ogen.plugin.EndpointInfo
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.OperationName
import de.quati.ogen.plugin.intern.model.Security
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecInfoContext
import de.quati.ogen.plugin.intern.model.Tag
import io.swagger.v3.oas.models.PathItem

internal class ParserContext(
    override val version: Spec.Version,
    override val defaultSecurity: Security,
    override val securityRequirementObjects: Map<String, Security.RequirementObject>,
    val operationIdGenerator: ((EndpointInfo) -> String?)?,
    val tagPathPrefixes: Map<Tag, String>,
) : SpecInfoContext {
    private val schemas = mutableMapOf<ComponentName.Schema, Component.Schema.NonInline>()
    private val reservedNames = mutableSetOf<ComponentName.Schema>()
    private val generatedOperationIds = mutableSetOf<GeneratedOperationId>()

    private data class GeneratedOperationId(
        val id: OperationName,
        val tag: Tag,
    )

    fun getSchemas() = schemas.toMap()
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
        method: PathItem.HttpMethod,
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

    fun checkOperationId(id: OperationName, tag: Tag) {
        val obj = GeneratedOperationId(id = id, tag = tag)
        if (obj in generatedOperationIds) error("Operation ID '$id' in tag '$tag' already exists")
        generatedOperationIds += obj
    }
}
