package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.decode.OasDecoder
import de.quati.ogen.plugin.intern.oas.model.Example
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.oas.model.SchemaSerializer
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.format.DateTimeFormatter

/** A schema's `default` that its schema doesn't allow: of another type, outside its `enum`, above its `maximum`, … */
internal fun invalidDefaultErrors(spec: ValidatedSpec): List<String> {
    val checker = ValueChecker(spec)
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val default = node.schema.default ?: return@walkSchemas
        checker.report("default", default, node.migrated, node.location)?.let(errors::add)
    }
    return errors
}

/**
 * Examples that their schema doesn't allow: a schema's `example` and `examples`, and the `example` and `examples` of
 * parameters, headers and media types. A media type's examples are checked only for JSON media types: those of others,
 * such as `application/xml`, are often the serialized text. An Example Object's `externalValue` is not read.
 */
internal fun invalidExampleWarnings(spec: ValidatedSpec): List<String> {
    val checker = ValueChecker(spec)
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        schema.example?.let { checker.report("example", it, node.migrated, node.location)?.let(warnings::add) }
        schema.examples?.forEachIndexed { i, example ->
            checker.report("examples[$i]", example, node.migrated, node.location)?.let(warnings::add)
        }
    }
    spec.exampleNodes().forEach { node ->
        val schema = node.schema?.takeIf { node.json } ?: return@forEach
        node.example?.let { checker.report("example", it, schema, node.location)?.let(warnings::add) }
        node.examples?.forEach { (name, refOr) ->
            val example = spec.example(refOr) ?: return@forEach
            example.value?.let { checker.report("example '$name'", it, schema, node.location)?.let(warnings::add) }
            example.dataValue?.let {
                checker.report("example '$name' (dataValue)", it, schema, node.location)?.let(warnings::add)
            }
        }
    }
    return warnings
}

/** The Example Object [refOr] is or refers to in `components.examples`; null if the reference doesn't resolve. */
private fun ValidatedSpec.example(refOr: RefOr<Example>): Example? {
    var current = refOr
    val seen = mutableSetOf<String>()
    while (true) {
        current = when (current) {
            is RefOr.Value -> return current.value
            is RefOr.Ref -> {
                val name = current.ref.componentName("examples")?.takeIf { seen.add(it) } ?: return null
                api.components?.examples?.get(name) ?: return null
            }
        }
    }
}

/**
 * Checks values against schemas, as JSON Schema 2020-12 does, with these exceptions:
 * - In 3.0, `null` is allowed in a nullable schema whose `enum` doesn't list it, as migration added `null` only to
 *   the type.
 * - A required property whose schema is `readOnly` or `writeOnly` may be missing: an example is of a request or of a
 *   response, which leave out one or the other.
 * - With a `discriminator` whose property the value has, a `oneOf` or `anyOf` checks only the schema it maps to.
 * - Formats are checked for `date`, `date-time`, `time`, `uuid`, `email`, `ipv4`, `int32` and `int64`; others pass.
 * - `unevaluatedProperties`, `unevaluatedItems`, and `$ref`s other than to `#/components/schemas/…` are not checked.
 */
internal class ValueChecker(private val spec: ValidatedSpec) {
    private val components = spec.api.components?.schemas.orEmpty()
    private val resolved = HashMap<String, Schema?>()

    /** A message for [value] at [location] if its schema doesn't allow it, null if it does. */
    fun report(what: String, value: JsonElement, schema: Schema, location: String): String? {
        val problems = problems(value, schema)
        if (problems.isEmpty()) return null
        return "$what in '$location' does not match its schema: ${problems.joinToString("; ")}"
    }

    /** Why the schema doesn't allow [value]; empty if it does. */
    fun problems(value: JsonElement, schema: Schema): List<String> = check(value, schema, "", emptySet())

    private fun allows(value: JsonElement, schema: Schema, refs: Set<String>) = check(value, schema, "", refs).isEmpty()

    /** [refs] are those followed for this value, to stop at a schema that refers to itself without a nested value. */
    private fun check(value: JsonElement, schema: Schema, at: String, refs: Set<String>): List<String> {
        val s = when (schema) {
            is Schema.Bool -> return if (schema.value) emptyList() else listOf(problem(at, "no value is allowed here"))
            is Schema.Obj -> schema
        }
        val problems = mutableListOf<String>()
        fun add(message: String) {
            problems += problem(at, message)
        }

        s.ref?.takeIf { it !in refs }?.let { ref -> resolve(ref)?.let { problems += check(value, it, at, refs + ref) } }

        val types = s.declaredTypes
        if (types.isNotEmpty() && types.none { value.hasType(it) }) {
            add("${value.describe()} is not of type ${types.typeList()}")
            return problems
        }
        s.enum?.takeIf { it.isNotEmpty() }?.let { values ->
            val impliedNull = spec.is30 && value is JsonNull && "null" in types
            if (!impliedNull && value.valueKey() !in values.map { it.valueKey() })
                add("${value.describe()} is not one of its enum values ${values.joinToString { it.render() }}")
        }
        s.const?.let { if (value.valueKey() != it.valueKey()) add("${value.describe()} is not its const value ${it.render()}") }

        when (value) {
            is JsonObject -> problems += checkObject(value, s, at, refs)
            is JsonArray -> problems += checkArray(value, s, at, refs)
            is JsonNull -> Unit
            is JsonPrimitive -> when (value.jsonType) {
                "number" -> checkNumber(value, s).forEach(::add)
                "string" -> checkString(value.content, s).forEach(::add)
            }
        }

        s.allOf?.forEach { problems += check(value, it, at, refs) }
        s.anyOf?.let { problems += checkAlternatives(value, s, it, "anyOf", at, refs) }
        s.oneOf?.let { problems += checkAlternatives(value, s, it, "oneOf", at, refs) }
        s.not?.let { if (allows(value, it, refs)) add("${value.describe()} matches its 'not' schema") }
        s.ifSchema?.let { condition ->
            val branch = if (allows(value, condition, refs)) s.thenSchema else s.elseSchema
            branch?.let { problems += check(value, it, at, refs) }
        }
        return problems
    }

    private fun checkAlternatives(
        value: JsonElement, schema: Schema.Obj, alternatives: List<Schema>, keyword: String, at: String, refs: Set<String>,
    ): List<String> {
        discriminated(value, schema, alternatives)?.let { return check(value, it, at, refs) }
        val matching = alternatives.count { allows(value, it, refs) }
        return when {
            matching == 1 || (matching > 1 && keyword == "anyOf") -> emptyList()
            matching > 1 -> listOf(problem(at, "${value.describe()} matches $matching of its oneOf alternatives, not one"))
            else -> {
                // Alternatives of another type are no candidates; if only one is left, its problems say the most.
                val candidates = alternatives.filter { typeAllows(value, it, refs) }
                candidates.singleOrNull()?.let { check(value, it, at, refs) }
                    ?: listOf(problem(at, "${value.describe()} matches none of its $keyword alternatives"))
            }
        }
    }

    /** The alternative the discriminator selects for [value], if it has the property and the mapping is known. */
    private fun discriminated(value: JsonElement, schema: Schema.Obj, alternatives: List<Schema>): Schema? {
        val discriminator = schema.discriminator ?: return null
        val name = discriminator.propertyName ?: return null
        val key = ((value as? JsonObject)?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val target = discriminator.mapping?.get(key)?.let { if (it.startsWith("#") || '/' in it) it else "$SCHEMAS$it" }
            ?: "$SCHEMAS$key"
        return alternatives.firstOrNull { (it as? Schema.Obj)?.ref == target }
    }

    /** Whether [schema]'s own `type`, or that of the schema it refers to, allows [value]'s type. */
    private fun typeAllows(value: JsonElement, schema: Schema, refs: Set<String>): Boolean {
        val s = schema as? Schema.Obj ?: return schema == Schema.Bool(true)
        val types = s.declaredTypes
        if (types.isNotEmpty() && types.none { value.hasType(it) }) return false
        val ref = s.ref?.takeIf { it !in refs } ?: return true
        return resolve(ref)?.let { typeAllows(value, it, refs + ref) } ?: true
    }

    private fun checkObject(value: JsonObject, s: Schema.Obj, at: String, refs: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        fun add(message: String) {
            problems += problem(at, message)
        }
        val properties = s.properties.orEmpty()
        s.required?.filter { it !in value && !properties[it].isReadOrWriteOnly(refs) }?.forEach {
            add("required property '$it' is missing")
        }
        s.minProperties?.let { if (value.size < it) add("has ${value.size} properties, fewer than minProperties $it") }
        s.maxProperties?.let { if (value.size > it) add("has ${value.size} properties, more than maxProperties $it") }
        val patterns = s.patternProperties.orEmpty().mapNotNull { (pattern, schema) -> pattern.regex()?.let { it to schema } }
        value.forEach { (name, child) ->
            val path = if (at.isEmpty()) name else "$at.$name"
            properties[name]?.let { problems += check(child, it, path, emptySet()) }
            val matched = patterns.filter { (regex, _) -> regex.containsMatchIn(name) }
            matched.forEach { (_, schema) -> problems += check(child, schema, path, emptySet()) }
            if (name !in properties && matched.isEmpty()) s.additionalProperties?.let { additional ->
                if (additional == Schema.Bool(false)) add("property '$name' is not allowed")
                else problems += check(child, additional, path, emptySet())
            }
            s.propertyNames?.let { names ->
                if (!allows(JsonPrimitive(name), names, emptySet())) add("property name '$name' does not match 'propertyNames'")
            }
        }
        s.dependentRequired?.forEach { (name, needed) ->
            if (name in value) needed.filter { it !in value }.forEach { add("property '$it' is missing, which '$name' requires") }
        }
        s.dependentSchemas?.forEach { (name, schema) -> if (name in value) problems += check(value, schema, at, refs) }
        return problems
    }

    private fun Schema?.isReadOrWriteOnly(refs: Set<String>): Boolean {
        val s = this as? Schema.Obj ?: return false
        if (s.readOnly == true || s.writeOnly == true) return true
        val ref = s.ref?.takeIf { it !in refs } ?: return false
        return resolve(ref).isReadOrWriteOnly(refs + ref)
    }

    private fun checkArray(value: JsonArray, s: Schema.Obj, at: String, refs: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        fun add(message: String) {
            problems += problem(at, message)
        }
        s.minItems?.let { if (value.size < it) add("has ${value.size} items, fewer than minItems $it") }
        s.maxItems?.let { if (value.size > it) add("has ${value.size} items, more than maxItems $it") }
        if (s.uniqueItems == true && value.map { it.valueKey() }.toSet().size < value.size) add("has duplicate items")
        val prefix = s.prefixItems.orEmpty()
        value.forEachIndexed { i, item ->
            val schema = prefix.getOrNull(i) ?: s.items ?: return@forEachIndexed
            problems += check(item, schema, "$at[$i]", emptySet())
        }
        s.contains?.let { contains ->
            val count = value.count { allows(it, contains, emptySet()) }
            val min = s.minContains ?: 1
            if (count < min) add("has $count items that match 'contains', fewer than $min")
            s.maxContains?.let { if (count > it) add("has $count items that match 'contains', more than $it") }
        }
        return problems
    }

    private fun checkNumber(value: JsonPrimitive, s: Schema.Obj): List<String> {
        val number = value.content.toBigDecimalOrNull() ?: return emptyList()
        val problems = mutableListOf<String>()
        val shown = value.content
        s.minimum?.let { if (number < it) problems += "$shown is less than minimum ${it.toPlainString()}" }
        s.exclusiveMinimum?.let { if (number <= it) problems += "$shown is not greater than exclusiveMinimum ${it.toPlainString()}" }
        s.maximum?.let { if (number > it) problems += "$shown is greater than maximum ${it.toPlainString()}" }
        s.exclusiveMaximum?.let { if (number >= it) problems += "$shown is not less than exclusiveMaximum ${it.toPlainString()}" }
        s.multipleOf?.takeIf { it.signum() > 0 }?.let {
            if (number.remainder(it).signum() != 0) problems += "$shown is not a multiple of ${it.toPlainString()}"
        }
        val range = when (s.format) {
            "int32" -> INT32
            "int64" -> INT64
            else -> null
        }
        if (range != null && (number < range.start || number > range.endInclusive))
            problems += "$shown is out of the range of format '${s.format}'"
        return problems
    }

    private fun checkString(value: String, s: Schema.Obj): List<String> {
        val problems = mutableListOf<String>()
        val shown = JsonPrimitive(value).render()
        val length = value.codePointCount(0, value.length)
        s.minLength?.let { if (length < it) problems += "$shown is shorter than minLength $it" }
        s.maxLength?.let { if (length > it) problems += "$shown is longer than maxLength $it" }
        s.pattern?.let { pattern ->
            val regex = pattern.regex()
            if (regex != null && !regex.containsMatchIn(value)) problems += "$shown does not match pattern '$pattern'"
        }
        s.format?.let { format ->
            val valid = formats[format] ?: return@let
            if (!valid(value)) problems += "$shown is not a valid $format"
        }
        return problems
    }

    /** The schema a `$ref` names: a component schema, or a schema inside one; null if unknown. */
    private fun resolve(ref: String): Schema? = resolved.getOrPut(ref) {
        val fragment = runCatching { URI(ref) }.getOrNull()?.takeIf { it.scheme == null && it.path.isNullOrEmpty() }
            ?.fragment ?: return@getOrPut null
        val segments = runCatching { JsonPointer.parse(fragment).segments }.getOrNull() ?: return@getOrPut null
        if (segments.size < 3 || segments[0] != "components" || segments[1] != "schemas") return@getOrPut null
        val component = components[segments[2]] ?: return@getOrPut null
        if (segments.size == 3) return@getOrPut component
        var element: JsonElement = OasDecoder.json.encodeToJsonElement(SchemaSerializer, component)
        for (segment in segments.drop(3)) {
            element = when (element) {
                is JsonObject -> element[segment]
                is JsonArray -> segment.toIntOrNull()?.let { element.getOrNull(it) }
                else -> null
            } ?: return@getOrPut null
        }
        runCatching { OasDecoder.json.decodeFromJsonElement(SchemaSerializer, element) }.getOrNull()
    }

    private companion object {
        const val SCHEMAS = "#/components/schemas/"
        val INT32 = BigDecimal(Int.MIN_VALUE)..BigDecimal(Int.MAX_VALUE)
        val INT64 = BigDecimal(Long.MIN_VALUE)..BigDecimal(Long.MAX_VALUE)
        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val EMAIL = Regex("[^@\\s]+@[^@\\s]+")
        val IPV4 = Regex("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)")

        fun parses(parse: (String) -> Any): (String) -> Boolean = { runCatching { parse(it) }.isSuccess }

        // RFC 3339, which allows `t` and `z` in lower case. A `time` without an offset passes, as it is often written.
        val formats: Map<String, (String) -> Boolean> = mapOf(
            "date" to parses { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) },
            "date-time" to parses { OffsetDateTime.parse(it.uppercase(), DateTimeFormatter.ISO_OFFSET_DATE_TIME) },
            "time" to { value ->
                parses { OffsetTime.parse(value.uppercase(), DateTimeFormatter.ISO_OFFSET_TIME) }(value) ||
                    parses { LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME) }(value)
            },
            "uuid" to UUID::matches,
            "email" to EMAIL::matches,
            "ipv4" to IPV4::matches,
        )

        fun problem(at: String, message: String) = if (at.isEmpty()) message else "at '$at': $message"

        // A pattern the JVM can't compile (a warning of its own) is not checked.
        fun String.regex(): Regex? = runCatching { Regex(this) }.getOrNull()
    }
}

/** Whether [this] is a value of the JSON Schema type [type]; an integer is a number without a fractional part. */
private fun JsonElement.hasType(type: String): Boolean = when (type) {
    "object" -> this is JsonObject
    "array" -> this is JsonArray
    "integer" -> jsonType == "number" && (this as JsonPrimitive).isWholeNumber()
    else -> jsonType == type
}

/** A value as messages show it: primitives as [render] shows them, but no whole objects or arrays. */
private fun JsonElement.describe(): String = when (this) {
    is JsonObject -> "an object"
    is JsonArray -> "an array"
    else -> render()
}

/** Equal for values that are equal in JSON, also inside objects and arrays: `1` and `1.0`, but not `1` and `"1"`. */
private fun JsonElement.valueKey(): Any = when (this) {
    is JsonObject -> mapValues { it.value.valueKey() }
    is JsonArray -> map { it.valueKey() }
    else -> enumKey()
}

private fun String.toBigDecimalOrNull() = runCatching { BigDecimal(this) }.getOrNull()
