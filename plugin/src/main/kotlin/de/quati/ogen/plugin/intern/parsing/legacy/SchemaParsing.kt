package de.quati.ogen.plugin.intern.parsing.legacy

import de.quati.kotlin.util.poet.toCamelCase
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.Constraints
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.TypeWithFormat
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation

context(c: ParserContext)
private fun io.swagger.v3.oas.models.media.Schema<*>.isNullable(): Boolean = when (c.version) {
    Spec.Version.V3_0 -> nullable ?: false
    Spec.Version.V3_1, Spec.Version.V3_2 -> types?.contains("null") ?: false
}

private fun io.swagger.v3.oas.models.media.Schema<*>.isExplicitNullType() =
    (type == "null" || types?.singleOrNull() == "null") && format == null

private fun io.swagger.v3.oas.models.media.Schema<*>.refObjOrNull() = `$ref`?.let(RefString.Schema::parse)
private fun io.swagger.v3.oas.models.media.Schema<*>.parseRefOrNull() = refObjOrNull()?.let {
    Component.Schema.Ref.create(
        name = it.schemaName,
        isNullable = false,
    )
}

private fun SchemaLocation.isValueSchema() = when (this) {
    is SchemaLocation.Root -> when(type) {
        SchemaLocation.Root.Type.COMPONENTS_SCHEMA -> true
        SchemaLocation.Root.Type.COMPONENTS_PARAMETER -> false
        SchemaLocation.Root.Type.PATH_BODY -> false
    }
    is SchemaLocation.Nested -> false
}

private fun createUnknown(
    location: SchemaLocation,
    typeWithFormat: TypeWithFormat?,
) = when (location.isValueSchema()) {
    true -> Component.Schema.Unknown.Value(location.name, typeWithFormat)
    false -> Component.Schema.Unknown.Inline(typeWithFormat)
}

private fun createPrimitive(
    location: SchemaLocation,
    type: Component.Schema.Primitiv.Type,
    format: String?,
    isNullable: Boolean,
    constraints: Constraints,
) = when (location.isValueSchema()) {
    true -> Component.Schema.Primitiv.Value(location.name, type, format, isNullable, constraints)
    false -> Component.Schema.Primitiv.Inline(type, format, isNullable, constraints)
}

private fun createArray(
    location: SchemaLocation,
    items: Component.Schema.Inline,
    isNullable: Boolean,
    typeWithFormat: TypeWithFormat?,
    constraints: Constraints,
) = when (location.isValueSchema()) {
    true -> Component.Schema.Array.Value(location.name, items, isNullable, typeWithFormat, constraints)
    false -> Component.Schema.Array.Inline(items, isNullable, typeWithFormat, constraints)
}

private fun createMap(
    location: SchemaLocation,
    valueSchema: Component.Schema.Inline,
    isNullable: Boolean,
    typeWithFormat: TypeWithFormat?,
    constraints: Constraints,
) = when (location.isValueSchema()) {
    true -> Component.Schema.MapS.Value(location.name, valueSchema, isNullable, typeWithFormat, constraints)
    false -> Component.Schema.MapS.Inline(valueSchema, isNullable, typeWithFormat, constraints)
}

/**
 * The validation keywords of this schema. In OpenAPI 3.0 `exclusiveMinimum` and `exclusiveMaximum` are flags
 * on `minimum` and `maximum`; in 3.1 they are bounds of their own, of which the tighter one counts.
 */
private fun io.swagger.v3.oas.models.media.Schema<*>.toConstraints() = Constraints(
    minLength = minLength,
    maxLength = maxLength,
    pattern = pattern,
    minimum = Constraints.tighterMinimum(
        minimum?.let { Constraints.Bound(it, inclusive = exclusiveMinimum != true) },
        exclusiveMinimumValue?.let { Constraints.Bound(it, inclusive = false) },
    ),
    maximum = Constraints.tighterMaximum(
        maximum?.let { Constraints.Bound(it, inclusive = exclusiveMaximum != true) },
        exclusiveMaximumValue?.let { Constraints.Bound(it, inclusive = false) },
    ),
    multipleOf = multipleOf,
    minItems = minItems,
    maxItems = maxItems,
    uniqueItems = uniqueItems == true,
    minProperties = minProperties,
    maxProperties = maxProperties,
)

private fun io.swagger.v3.oas.models.media.Schema<*>.toUnknown(location: SchemaLocation) = createUnknown(
    location = location,
    typeWithFormat = toTypeWithFormatOrNull(),
)

private fun io.swagger.v3.oas.models.media.Schema<*>.toTypeWithFormatOrNull() = type?.let {
    TypeWithFormat(type = it, format = format)
}

context(pc: ParserContext)
internal fun io.swagger.v3.oas.models.media.Schema<*>.parse(
    location: SchemaLocation,
): Component.Schema.Inline = pc.registerSchema(location = location) { location ->
    when {
        isExplicitNullType() -> Component.Schema.Null
        `$ref` != null -> parseRefOrNull()!!
        not != null -> toUnknown(location)
        oneOf != null -> run {
            val disc = discriminator?.let {
                it.parse() ?: return@run toUnknown(location)
            }
            val isNullable = isNullable() || oneOf!!.any { it.isExplicitNullType() }
            val rawSchemasNonNull = oneOf!!.filterNot { it.isExplicitNullType() }
            if (additionalProperties != null) return@run toUnknown(location)
            if (properties?.isNotEmpty() == true) return@run toUnknown(location)
            if (disc == null)
                return@run rawSchemasNonNull.singleOrNull()
                    ?.parse(location)
                    ?.copySchema { it || isNullable }
                    ?: toUnknown(location)
            if (disc.mapping != null && disc.mapping.size != rawSchemasNonNull.size)
                return@run toUnknown(location)
            val mappingReversed = disc.mapping?.entries?.associate { it.value to it.key }
            val innerSchemas = rawSchemasNonNull.associate { schema ->
                val refItem = schema.refObjOrNull() ?: return@run toUnknown(location)
                val itemName = if (mappingReversed == null)
                    refItem.schemaName.rawClassName
                else
                    mappingReversed[refItem] ?: return@run toUnknown(location)
                val refSchema = schema.parseRefOrNull() ?: return@run toUnknown(location)
                itemName to refSchema
            }

            Component.Schema.SealedInterface(
                isNullable = isNullable,
                schemas = innerSchemas,
                discriminatorName = disc.propertyName,
                name = location.name,
                typeWithFormat = toTypeWithFormatOrNull()
            )
        }

        anyOf != null || allOf != null -> run {
            if (anyOf != null && allOf != null) return@run toUnknown(location)
            val (type, rawSchemas) = anyOf?.let { Component.Schema.Composed.Type.AnyOf to it }
                ?: allOf!!.let { Component.Schema.Composed.Type.AllOf to it }
            val isNullable = isNullable() || rawSchemas.any { it.isExplicitNullType() }
            if (discriminator != null) return@run toUnknown(location)
            if (additionalProperties != null) return@run toUnknown(location)
            if (properties?.isNotEmpty() == true) return@run toUnknown(location)
            val nonNullSchemas = rawSchemas.filterNot { it.isExplicitNullType() }
            nonNullSchemas.singleOrNull()
                ?.parse(location)
                ?.copySchema { it || isNullable }
                ?: Component.Schema.Composed(
                    type = type,
                    isNullable = isNullable,
                    schemas = nonNullSchemas.mapIndexed { index, schema ->
                        schema.parse(location = location + "Part$index")
                    },
                    name = location.name,
                    typeWithFormat = toTypeWithFormatOrNull(),
                )
        }

        enum != null -> run {
            val isNullable = isNullable()
            if (enum!!.any { it !is String })
                return@run toUnknown(location)
            val values = enum!!.map { it as String }
                .filterNot { isNullable && it == "null" }
            Component.Schema.EnumString(
                isNullable = isNullable(),
                values = values,
                name = location.name,
                typeWithFormat = toTypeWithFormatOrNull(),
            )
        }

        else -> when (val type = types?.singleOrNull() ?: types?.singleOrNull { it != "null" }) {
            "null" -> Component.Schema.Null
            null -> toUnknown(location)

            "object" -> run {
                val props = properties?.mapValues {
                    it.value.parse(location = location + it.key.toCamelCase(capitalized = true))
                } ?: emptyMap()
                val additionalProps = additionalProperties ?: return@run if (props.isEmpty())
                    toUnknown(location)
                else
                    Component.Schema.Obj(
                        isNullable = isNullable(),
                        required = required?.toSet() ?: emptySet(),
                        properties = props,
                        name = location.name,
                        typeWithFormat = toTypeWithFormatOrNull()
                    )
                if (additionalProps == true)
                    return@run toUnknown(location)
                if (!(properties?.isEmpty() ?: true))
                    return@run toUnknown(location)
                if (additionalProps !is io.swagger.v3.oas.models.media.Schema<*>)
                    return@run toUnknown(location)
                createMap(
                    location = location,
                    isNullable = isNullable(),
                    valueSchema = additionalProps.parse(
                        location = when (location.isValueSchema()) {
                            true -> location + "Value"
                            false -> location.updateLast { "${it}Value" }
                        },
                    ),
                    typeWithFormat = toTypeWithFormatOrNull(),
                    constraints = toConstraints(),
                )
            }

            "array" -> run {
                val items = items ?: return@run toUnknown(location)
                createArray(
                    location = location,
                    isNullable = isNullable(),
                    items = items.parse(
                        location = when (location.isValueSchema()) {
                            true -> location + "Item"
                            false -> location.updateLast { "${it}Item" }
                        },
                    ),
                    typeWithFormat = toTypeWithFormatOrNull(),
                    constraints = toConstraints(),
                )
            }

            else -> run {
                val typeE = Component.Schema.Primitiv.Type.entries
                    .find { it.name.equals(type, ignoreCase = true) }
                    ?: return@run toUnknown(location)

                createPrimitive(
                    location = location,
                    type = typeE,
                    format = format,
                    isNullable = isNullable(),
                    constraints = toConstraints(),
                )
            }
        }
    }
}
