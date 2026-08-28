package de.quati.ogen.plugin.intern.parsing

import de.quati.kotlin.util.poet.toCamelCase
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecInfoContext
import de.quati.ogen.plugin.intern.model.TypeWithFormat

context(c: SpecInfoContext)
private fun io.swagger.v3.oas.models.media.Schema<*>.isNullable(): Boolean = when (c.version) {
    Spec.Version.V3_0 -> nullable ?: false
    Spec.Version.V3_1 -> types?.contains("null") ?: false
}

private fun io.swagger.v3.oas.models.media.Schema<*>.isExplicitNullType() =
    (type == "null" || types?.singleOrNull() == "null") && format == null

private fun io.swagger.v3.oas.models.media.Schema<*>.refObjOrNull() = `$ref`?.let(RefString.Schema::parse)
private fun io.swagger.v3.oas.models.media.Schema<*>.parseRefOrNull(name: ComponentName.Schema) = refObjOrNull()?.let {
    Component.Schema.Ref(
        name = name,
        ref = it,
        isNullable = false,
    )
}

private fun io.swagger.v3.oas.models.media.Schema<*>.toUnknown(name: ComponentName.Schema) =
    Component.Schema.Unknown(name = name, typeWithFormat = toTypeWithFormatOrNull())

private fun io.swagger.v3.oas.models.media.Schema<*>.toTypeWithFormatOrNull() = type?.let {
    TypeWithFormat(type = it, format = format)
}

context(_: SpecInfoContext)
internal fun io.swagger.v3.oas.models.media.Schema<*>.parse(name: ComponentName.Schema): Component.Schema = when {
    isExplicitNullType() -> Component.Schema.Null
    `$ref` != null -> parseRefOrNull(name = name)!!
    not != null -> toUnknown(name = name)
    oneOf != null -> run {
        val disc = discriminator?.let {
            it.parse() ?: return@run toUnknown(name = name)
        }
        val isNullable = isNullable() || oneOf!!.any { it.isExplicitNullType() }
        val rawSchemasNonNull = oneOf!!.filterNot { it.isExplicitNullType() }
        if (additionalProperties != null) return@run toUnknown(name = name)
        if (properties?.isNotEmpty() == true) return@run toUnknown(name = name)
        if (disc == null)
            return@run rawSchemasNonNull
                .map { it.parse(name = name) }
                .singleOrNull()
                ?.copySchema { it || isNullable }
                ?: toUnknown(name = name)
        if (disc.mapping != null && disc.mapping.size != rawSchemasNonNull.size)
            return@run toUnknown(name = name)
        val mappingReversed = disc.mapping?.entries?.associate { it.value to it.key }
        val innerSchemas = rawSchemasNonNull.associate { schema ->
            val refItem = schema.refObjOrNull() ?: return@run toUnknown(name = name)
            val itemName = if (mappingReversed == null)
                refItem.schemaName.rawClassName
            else
                mappingReversed[refItem] ?: return@run toUnknown(name = name)
            itemName to schema.parseRefOrNull(name = name + itemName.toCamelCase(capitalized = true))!!
        }

        Component.Schema.SealedInterface(
            isNullable = isNullable,
            schemas = innerSchemas,
            discriminatorName = disc.propertyName,
            name = name,
            typeWithFormat = toTypeWithFormatOrNull()
        )
    }

    anyOf != null || allOf != null -> run {
        if (anyOf != null && allOf != null) return@run toUnknown(name = name)
        val (type, rawSchemas) = anyOf?.let { Component.Schema.Composed.Type.AnyOf to it }
            ?: allOf!!.let { Component.Schema.Composed.Type.AllOf to it }
        val isNullable = isNullable() || rawSchemas.any { it.isExplicitNullType() }
        if (discriminator != null) return@run toUnknown(name = name)
        if (additionalProperties != null) return@run toUnknown(name = name)
        if (properties?.isNotEmpty() == true) return@run toUnknown(name = name) // TODO
        val nonNullSchemas = rawSchemas.filterNot { it.isExplicitNullType() }
        nonNullSchemas.singleOrNull()
            ?.parse(name = name)
            ?.copySchema { it || isNullable }
            ?: Component.Schema.Composed(
                type = type,
                isNullable = isNullable,
                schemas = nonNullSchemas.map { it.parse(name = ComponentName.Schema.Unnamed) },
                name = name,
                typeWithFormat = toTypeWithFormatOrNull(),
            )
    }

    enum != null -> run {
        val isNullable = isNullable()
        if (enum!!.any { it !is String })
            return@run toUnknown(name = name)
        val values = enum!!.map { it as String }
            .filterNot { isNullable && it == "null" }
        Component.Schema.EnumString(
            isNullable = isNullable(),
            values = values,
            name = name,
            typeWithFormat = toTypeWithFormatOrNull(),
        )
    }

    else -> when (val type = types?.singleOrNull() ?: types?.singleOrNull { it != "null" }) {
        "null" -> Component.Schema.Null
        null -> toUnknown(name = name)

        "object" -> run {
            val props = properties?.mapValues {
                it.value.parse(name = name + it.key.toCamelCase(capitalized = true))
            } ?: emptyMap()
            val additionalProps = additionalProperties ?: return@run if (props.isEmpty())
                toUnknown(name = name)
            else
                Component.Schema.Obj(
                    isNullable = isNullable(),
                    required = required?.toSet() ?: emptySet(),
                    properties = props,
                    name = name,
                    typeWithFormat = toTypeWithFormatOrNull()
                )
            if (additionalProps == true)
                return@run toUnknown(name = name)
            if (!(properties?.isEmpty() ?: true))
                return@run toUnknown(name = name)
            if (additionalProps !is io.swagger.v3.oas.models.media.Schema<*>)
                return@run toUnknown(name = name)
            Component.Schema.MapS(
                isNullable = isNullable(),
                valueSchema = additionalProps.parse(name = name + "Value"),
                name = name,
                typeWithFormat = toTypeWithFormatOrNull()
            )
        }

        "array" -> run {
            val items = items ?: return@run toUnknown(name = name)
            Component.Schema.Array(
                isNullable = isNullable(),
                items = items.parse(name = name.updateLast { "${it}Item" }),
                name = name,
                typeWithFormat = toTypeWithFormatOrNull()
            )
        }

        else -> run {
            val typeE = Component.Schema.PrimitivType.Type.entries
                .find { it.name.equals(type, ignoreCase = true) }
                ?: return@run Component.Schema.Unknown(
                    name = name,
                    typeWithFormat = toTypeWithFormatOrNull()
                )

            Component.Schema.PrimitivType(
                type = typeE,
                format = format,
                isNullable = isNullable(),
                name = name,
            )
        }
    }
}
