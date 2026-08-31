package de.quati.ogen.plugin.intern.parsing.helper

import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Security
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecInfoContext

internal class ParserContext(
    override val version: Spec.Version,
    override val defaultSecurity: Security,
    override val securityRequirementObjects: Map<String, Security.RequirementObject>,
) : SpecInfoContext {
    private val schemas = mutableMapOf<ComponentName.Schema, Component.Schema.NonInline>()
    private val reservedNames = mutableSetOf<ComponentName.Schema>()

    fun getSchemas() = schemas.toMap()
    fun registerSchema(
        location: SchemaLocation,
        schemaGen: (SchemaLocation) -> Component.Schema,
    ): Component.Schema.Inline {
        val blacklist = schemas.keys + reservedNames // TODO + setOf("Serializer")
        val location = when(location) {
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
}
