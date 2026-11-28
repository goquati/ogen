package de.quati.ogen.plugin.intern.parsing

import de.quati.kotlin.util.poet.toCamelCase
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.Constraints
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.TypeWithFormat
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * A schema as code generation reads it: the fields swagger-parser's model had, derived from the OAS model.
 *
 * In 3.0, swagger-parser inferred the type (`items` → array, `additionalProperties` → object) and set `types`
 * only for the types it has classes for; `type` held the effective type. In 3.1 it set only `types`, from the
 * declared types or the first enum value. Nullability comes from a `"null"` type, which migration added for 3.0's
 * `nullable`.
 */
private class SchemaView(val obj: Schema.Obj, is30: Boolean, nullable: Boolean = false) {
    private val declared = obj.type
    val isNullable = nullable || declared?.contains("null") == true

    /** The effective type of 3.0, as swagger-parser's `type`; null in 3.1, where swagger-parser left it empty. */
    val type: String? = if (!is30) null else when {
        obj.items != null -> "array"
        obj.additionalProperties != null -> "object"
        declared != null -> declared.firstOrNull { it != "null" } ?: declared.firstOrNull()
        else -> obj.enum?.let(::inferTypeFromEnum)
    }

    val types: List<String>? = if (is30) when {
        obj.items != null -> listOf("array")
        obj.additionalProperties != null -> listOf("object")
        type in TYPED_IN_30 || type == "null" -> listOf(type!!)
        else -> null
    } else declared ?: obj.enum?.let { listOf(inferTypeFromEnum(it)) }

    val format get() = obj.format
    val ref get() = obj.ref

    /**
     * The enum values as swagger-parser's model had them: a 3.0 string schema cast them to text, unless its format
     * made them dates, UUIDs or bytes; otherwise strings stay strings and anything else is no string.
     */
    val enum: List<Any?>? = obj.enum?.let { values ->
        val declaredType = declared?.firstOrNull { it != "null" }.takeIf { is30 && obj.items == null && obj.additionalProperties == null }
        val castToText = declaredType == "string"
        values.map { value ->
            when {
                value is JsonNull -> null
                // IntegerSchema, NumberSchema and BooleanSchema cast to their type, which is never a string.
                declaredType in setOf("integer", "number", "boolean") -> NotAString
                castToText && obj.format in NON_STRING_FORMATS -> NotAString
                castToText && value is JsonPrimitive -> value.content
                value is JsonPrimitive && value.isString -> value.content
                else -> NotAString
            }
        }
    }

    val isExplicitNullType get() = (type == "null" || types?.singleOrNull() == "null") && format == null

    private object NotAString

    companion object {
        val TYPED_IN_30 = setOf("integer", "number", "boolean", "string", "object")
        val NON_STRING_FORMATS = setOf("date", "date-time", "uuid", "byte", "binary")

        /** swagger-parser's inference: the type of the first value, string for anything but booleans and numbers. */
        fun inferTypeFromEnum(values: List<JsonElement>): String {
            val first = values.firstOrNull() as? JsonPrimitive
            return when {
                first == null || first is JsonNull || first.isString -> "string"
                first.booleanOrNull != null -> "boolean"
                else -> "number"
            }
        }
    }
}

private val EMPTY = Schema.Obj()
private fun Schema.obj() = when (this) {
    is Schema.Obj -> this
    // swagger-parser read a boolean schema as one without keywords.
    is Schema.Bool -> EMPTY
}

context(c: ParserContext)
private fun Schema.view() = SchemaView(obj(), is30 = c.version == Spec.Version.V3_0)

context(_: ParserContext)
private fun Schema.isExplicitNullType() = view().isExplicitNullType

private fun SchemaView.refObjOrNull() = ref?.let(RefString.Schema::parse)
private fun SchemaView.parseRefOrNull() = refObjOrNull()?.let {
    Component.Schema.Ref.create(
        name = it.schemaName,
        isNullable = false,
    )
}

context(_: ParserContext)
private fun Schema.refObjOrNull() = view().refObjOrNull()

context(_: ParserContext)
private fun Schema.parseRefOrNull() = view().parseRefOrNull()

private fun SchemaLocation.isValueSchema() = when (this) {
    is SchemaLocation.Root -> when (type) {
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

/** The validation keywords; after migration, `exclusiveMinimum` and `exclusiveMaximum` are bounds of their own. */
private fun SchemaView.toConstraints() = Constraints(
    minLength = obj.minLength,
    maxLength = obj.maxLength,
    pattern = obj.pattern,
    minimum = Constraints.tighterMinimum(
        obj.minimum?.let { Constraints.Bound(it, inclusive = true) },
        obj.exclusiveMinimum?.let { Constraints.Bound(it, inclusive = false) },
    ),
    maximum = Constraints.tighterMaximum(
        obj.maximum?.let { Constraints.Bound(it, inclusive = true) },
        obj.exclusiveMaximum?.let { Constraints.Bound(it, inclusive = false) },
    ),
    multipleOf = obj.multipleOf,
    minItems = obj.minItems,
    maxItems = obj.maxItems,
    uniqueItems = obj.uniqueItems == true,
    minProperties = obj.minProperties,
    maxProperties = obj.maxProperties,
)

private fun SchemaView.toTypeWithFormatOrNull() = type?.let { TypeWithFormat(type = it, format = format) }

private fun SchemaView.toUnknown(location: SchemaLocation) = createUnknown(
    location = location,
    typeWithFormat = toTypeWithFormatOrNull(),
)

/**
 * Migration writes a nullable `allOf` of 3.0 as `anyOf: [{allOf: [...]}, {type: "null"}]`; read back as the
 * nullable `allOf` it was, so the nesting adds no level (and no name) that swagger-parser's model did not have.
 */
private fun Schema.Obj.unwrapMigratedNullableAllOf(): SchemaView? {
    val alternatives = anyOf ?: return null
    if (allOf != null || alternatives.size != 2) return null
    val nullType = alternatives.firstOrNull { it is Schema.Obj && it == Schema.Obj(type = listOf("null")) } ?: return null
    val inner = alternatives.first { it !== nullType } as? Schema.Obj ?: return null
    if (inner.allOf == null || inner != Schema.Obj(allOf = inner.allOf)) return null
    return SchemaView(copy(anyOf = null, allOf = inner.allOf), is30 = true, nullable = true)
}

/**
 * The schema of the values of the keys an object allows beyond its `properties`, or null when it allows none a map
 * keeps (#108).
 *
 * Without `additionalProperties`, other keys are ignored, as they always were; with `false` there are none. All keys
 * `patternProperties` and `additionalProperties` allow share one map: its values have their schema when they all have
 * the same one, otherwise they are untyped. Without `additionalProperties`, keys that match no pattern are allowed
 * too, with any value. A pattern whose schema is `false` allows no key.
 */
private fun Schema.Obj.additionalValueSchemaOrNull(): Schema? {
    val patterns = patternProperties.orEmpty().values.filter { it != Schema.Bool(false) }
    val additional = when (val it = additionalProperties) {
        null -> if (patterns.isEmpty()) return null else Schema.Bool(true)
        Schema.Bool(false) -> null
        else -> it
    }
    val schemas = (patterns + listOfNotNull(additional)).distinct()
    return when {
        schemas.isEmpty() -> null
        schemas.size == 1 -> schemas.single()
        else -> Schema.Bool(true)
    }
}

/**
 * The schema of the value of [name], a key this object requires but doesn't declare in `properties` (#121), or null
 * when the object doesn't allow it freely.
 *
 * In JSON Schema such a key is validated by the `patternProperties` whose patterns match it, otherwise by
 * `additionalProperties`; several matching patterns with different schemas leave the value untyped. Patterns are
 * matched with the JVM's regex, unanchored as in JSON Schema. Without `additionalProperties` and without a matching
 * pattern the key is allowed freely only if there are patterns at all: an object without either (an `allOf` member
 * requiring what a sibling defines, say) leaves the key to the schema that defines it.
 */
private fun Schema.Obj.undeclaredRequiredSchemaOrNull(name: String): Schema? {
    val matching = patternProperties.orEmpty()
        .filterKeys { pattern -> runCatching { Regex(pattern).containsMatchIn(name) }.getOrDefault(false) }
        .values.distinct()
    return when {
        Schema.Bool(false) in matching -> null
        matching.size == 1 -> matching.single()
        matching.isNotEmpty() -> Schema.Bool(true)
        else -> when (val it = additionalProperties) {
            null -> if (patternProperties.isNullOrEmpty()) null else Schema.Bool(true)
            Schema.Bool(false) -> null
            else -> it
        }
    }
}

context(pc: ParserContext)
internal fun Schema.parse(
    location: SchemaLocation,
): Component.Schema.Inline = pc.registerSchema(location = location) { location ->
    val s = (this as? Schema.Obj)?.takeIf { pc.version == Spec.Version.V3_0 }?.unwrapMigratedNullableAllOf() ?: view()
    val o = s.obj
    when {
        s.isExplicitNullType -> Component.Schema.Null
        s.ref != null -> s.parseRefOrNull()!!
        o.not != null -> s.toUnknown(location)
        o.oneOf != null -> run {
            val disc = o.discriminator?.let {
                it.parse() ?: return@run s.toUnknown(location)
            }
            val isNullable = s.isNullable || o.oneOf.any { it.isExplicitNullType() }
            val rawSchemasNonNull = o.oneOf.filterNot { it.isExplicitNullType() }
            if (o.additionalProperties != null) return@run s.toUnknown(location)
            if (o.properties?.isNotEmpty() == true) return@run s.toUnknown(location)
            if (disc == null)
                return@run rawSchemasNonNull.singleOrNull()
                    ?.parse(location)
                    ?.copySchema { it || isNullable }
                    ?: s.toUnknown(location)
            if (disc.mapping != null && disc.mapping.size != rawSchemasNonNull.size)
                return@run s.toUnknown(location)
            val mappingReversed = disc.mapping?.entries?.associate { it.value to it.key }
            val innerSchemas = rawSchemasNonNull.associate { schema ->
                val refItem = schema.refObjOrNull() ?: return@run s.toUnknown(location)
                val itemName = if (mappingReversed == null)
                    refItem.schemaName.rawClassName
                else
                    mappingReversed[refItem] ?: return@run s.toUnknown(location)
                val refSchema = schema.parseRefOrNull() ?: return@run s.toUnknown(location)
                itemName to refSchema
            }

            Component.Schema.SealedInterface(
                isNullable = isNullable,
                schemas = innerSchemas,
                discriminatorName = disc.propertyName,
                name = location.name,
                typeWithFormat = s.toTypeWithFormatOrNull()
            )
        }

        o.anyOf != null || o.allOf != null -> run {
            if (o.anyOf != null && o.allOf != null) return@run s.toUnknown(location)
            val (type, rawSchemas) = o.anyOf?.let { Component.Schema.Composed.Type.AnyOf to it }
                ?: o.allOf!!.let { Component.Schema.Composed.Type.AllOf to it }
            val isNullable = s.isNullable || rawSchemas.any { it.isExplicitNullType() }
            if (o.discriminator != null) return@run s.toUnknown(location)
            if (o.additionalProperties != null) return@run s.toUnknown(location)
            if (o.properties?.isNotEmpty() == true) return@run s.toUnknown(location)
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
                    typeWithFormat = s.toTypeWithFormatOrNull(),
                )
        }

        s.enum != null -> run {
            val isNullable = s.isNullable
            if (s.enum.any { it !is String })
                return@run s.toUnknown(location)
            val values = s.enum.map { it as String }
                .filterNot { isNullable && it == "null" }
            Component.Schema.EnumString(
                isNullable = isNullable,
                values = values,
                name = location.name,
                typeWithFormat = s.toTypeWithFormatOrNull(),
            )
        }

        else -> when (val type = s.types?.singleOrNull() ?: s.types?.singleOrNull { it != "null" }) {
            "null" -> Component.Schema.Null
            null -> s.toUnknown(location)

            "object" -> run {
                // A required key that `properties` doesn't declare has the schema of the pattern or additional
                // properties that allow it; as a property of its own, it is checked like any other (#121).
                val declared = o.properties.orEmpty()
                val undeclaredRequired = o.required.orEmpty().filter { it !in declared }.distinct()
                    .mapNotNull { name -> o.undeclaredRequiredSchemaOrNull(name)?.let { name to it } }
                val declaredProps = declared.mapValues {
                    it.value.parse(location = location + it.key.toCamelCase(capitalized = true))
                }
                if (declaredProps.isNotEmpty() || undeclaredRequired.isNotEmpty()) {
                    val additionalRaw = o.additionalValueSchemaOrNull()
                    val additional = additionalRaw?.parse(location = location + "AdditionalProperty")
                    // A key with the schema of the map's values has their type, not a copy of it.
                    val undeclaredProps = undeclaredRequired.associate { (name, schema) ->
                        name to (additional.takeIf { schema == additionalRaw }
                            ?: schema.parse(location = location + name.toCamelCase(capitalized = true)))
                    }
                    return@run Component.Schema.Obj(
                        isNullable = s.isNullable,
                        required = o.required?.toSet() ?: emptySet(),
                        properties = declaredProps + undeclaredProps,
                        name = location.name,
                        typeWithFormat = s.toTypeWithFormatOrNull(),
                        additionalProperties = additional,
                    )
                }
                // Without properties, `true` is the same as `{}`: a map of untyped values. An object that must stay
                // empty (`false`) and one that says nothing about its properties are untyped values (#98).
                val valueSchema = o.additionalValueSchemaOrNull() ?: return@run s.toUnknown(location)
                createMap(
                    location = location,
                    isNullable = s.isNullable,
                    valueSchema = valueSchema.parse(
                        location = when (location.isValueSchema()) {
                            true -> location + "Value"
                            false -> location.updateLast { "${it}Value" }
                        },
                    ),
                    typeWithFormat = s.toTypeWithFormatOrNull(),
                    constraints = s.toConstraints(),
                )
            }

            "array" -> run {
                val items = o.items ?: return@run s.toUnknown(location)
                createArray(
                    location = location,
                    isNullable = s.isNullable,
                    items = items.parse(
                        location = when (location.isValueSchema()) {
                            true -> location + "Item"
                            false -> location.updateLast { "${it}Item" }
                        },
                    ),
                    typeWithFormat = s.toTypeWithFormatOrNull(),
                    constraints = s.toConstraints(),
                )
            }

            else -> run {
                val typeE = Component.Schema.Primitiv.Type.entries
                    .find { it.name.equals(type, ignoreCase = true) }
                    ?: return@run s.toUnknown(location)

                createPrimitive(
                    location = location,
                    type = typeE,
                    format = s.format,
                    isNullable = s.isNullable,
                    constraints = s.toConstraints(),
                )
            }
        }
    }
}
