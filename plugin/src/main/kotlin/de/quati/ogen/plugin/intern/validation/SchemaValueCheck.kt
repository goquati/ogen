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

/**
 * A schema's `default` that its schema doesn't allow: of another type, outside its `enum`, above its `maximum`, …
 * `format` is not checked: JSON Schema only annotates with it, and an empty string is a common default.
 */
internal fun invalidDefaultErrors(spec: ValidatedSpec): List<String> {
    val checker = ValueChecker(spec, checkFormats = false)
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val default = node.schema.default ?: return@walkSchemas
        checker.report("default", default, node.pointer / "default", node.migrated, node.location)?.let(errors::add)
    }
    return errors
}

/**
 * Examples that their schema doesn't allow: a schema's `example` and `examples`, and the `example` and `examples` of
 * parameters, headers and media types. A media type's examples are checked only for JSON media types: those of others,
 * such as `application/xml` or a wildcard, are often the serialized text. An Example Object's `externalValue` is not read.
 */
internal fun invalidExampleWarnings(spec: ValidatedSpec): List<String> {
    val checker = ValueChecker(spec, checkFormats = true)
    val warnings = mutableListOf<String>()
    fun report(what: String, value: JsonElement, pointer: JsonPointer, schema: Schema, location: String) {
        checker.report(what, value, pointer, schema, location)?.let(warnings::add)
    }
    spec.walkSchemas { node ->
        val schema = node.schema
        schema.example?.let { report("example", it, node.pointer / "example", node.migrated, node.location) }
        schema.examples?.forEachIndexed { i, example ->
            report("examples[$i]", example, node.pointer / "examples" / i, node.migrated, node.location)
        }
    }
    spec.exampleNodes().forEach { node ->
        val schema = node.schema?.takeIf { node.json } ?: return@forEach
        node.example?.let { report("example", it, node.pointer / "example", schema, node.location) }
        node.examples?.forEach { (name, refOr) ->
            val (example, at) = spec.example(refOr, node.pointer / "examples" / name) ?: return@forEach
            example.value?.let { report("example '$name'", it, at / "value", schema, node.location) }
            example.dataValue?.let { report("example '$name' (dataValue)", it, at / "dataValue", schema, node.location) }
        }
    }
    return warnings
}

/**
 * The Example Object [refOr] at [pointer] is or refers to in `components.examples`, with its place; null if the
 * reference doesn't resolve.
 */
private fun ValidatedSpec.example(refOr: RefOr<Example>, pointer: JsonPointer): Pair<Example, JsonPointer>? {
    var current = refOr
    var at = pointer
    val seen = mutableSetOf<String>()
    while (true) {
        current = when (current) {
            is RefOr.Value -> return current.value to at
            is RefOr.Ref -> {
                val name = current.ref.componentName("examples")?.takeIf { seen.add(it) } ?: return null
                at = JsonPointer.of("components", "examples", name)
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
 *   response, which leave out one or the other. The property may be defined in another schema the value must match,
 *   such as another `allOf` member.
 * - With a `discriminator` whose property the value has, a `oneOf` or `anyOf` checks only the schema it maps to,
 *   or the one 3.2's `defaultMapping` names.
 * - With [checkFormats], formats are checked for `date`, `date-time`, `time`, `uuid`, `email`, `ipv4`, `int32` and
 *   `int64`; others pass.
 * - `unevaluatedProperties`, `unevaluatedItems`, and `$ref`s other than to `#/components/schemas/…` are not checked.
 *
 * A value the reader took for a boolean, number or null only as YAML 1.1 has it (`no`, `12:30`) is shown as written.
 */
internal class ValueChecker(private val spec: ValidatedSpec, private val checkFormats: Boolean) {
    private val components = spec.api.components?.schemas.orEmpty()
    private val resolved = HashMap<String, Schema?>()

    /** A message for [value] at [location] if its schema doesn't allow it, null if it does; [pointer] is its place. */
    fun report(what: String, value: JsonElement, pointer: JsonPointer, schema: Schema, location: String): String? {
        val problems = check(Value(value, pointer), schema, "", emptySet(), emptySet()).distinct()
        if (problems.isEmpty()) return null
        return "$what in '$location' does not match its schema: ${problems.joinToString("; ")}"
    }

    /** A value and its place in the bundle, to show it as written. */
    private inner class Value(val json: JsonElement, val pointer: JsonPointer) {
        fun child(key: String) = Value((json as JsonObject).getValue(key), pointer / key)
        fun child(index: Int) = Value((json as JsonArray)[index], pointer / index)

        /** As messages show it, `no` as written, with what YAML 1.1 reads, if that is no string. */
        fun shown(): String = spec.yaml11Text(pointer)
            ?.let { "$it (unquoted, so YAML 1.1 reads it as ${json.describe()}; quote it to make it a string)" }
            ?: json.describe()
    }

    private fun allows(value: Value, schema: Schema, refs: Set<String>, exempt: Set<String>) =
        check(value, schema, "", refs, exempt).isEmpty()

    /**
     * [refs] are those followed for this value, to stop at a schema that refers to itself without a nested value.
     * [exempt] are the required properties that may be missing, by what the schemas applied to this value define.
     */
    private fun check(value: Value, schema: Schema, at: String, refs: Set<String>, exempt: Set<String>): List<String> {
        val s = when (schema) {
            is Schema.Bool -> return if (schema.value) emptyList() else listOf(problem(at, "no value is allowed here"))
            is Schema.Obj -> schema
        }
        val json = value.json
        val problems = mutableListOf<String>()
        fun add(message: String) {
            problems += problem(at, message)
        }
        val exemptHere = if (json is JsonObject) exempt + s.readOrWriteOnlyNames(refs) else exempt
        fun applied(applied: Schema, refs: Set<String> = emptySet()) = check(value, applied, at, refs, exemptHere)

        s.ref?.takeIf { it !in refs }?.let { ref -> resolve(ref)?.let { problems += applied(it, refs + ref) } }

        val types = s.declaredTypes
        if (types.isNotEmpty() && types.none { json.hasType(it) }) {
            add("${value.shown()} is not of type ${types.typeList()}")
            return problems
        }
        s.enum?.takeIf { it.isNotEmpty() }?.let { values ->
            val impliedNull = spec.is30 && json is JsonNull && "null" in types
            if (!impliedNull && json.valueKey() !in values.map { it.valueKey() })
                add("${value.shown()} is not one of its enum values ${values.joinToString { it.render() }}")
        }
        s.const?.let { if (json.valueKey() != it.valueKey()) add("${value.shown()} is not its const value ${it.render()}") }

        when (json) {
            is JsonObject -> problems += checkObject(value, s, at, refs, exemptHere)
            is JsonArray -> problems += checkArray(value, s, at)
            is JsonNull -> Unit
            is JsonPrimitive -> when (json.jsonType) {
                "number" -> checkNumber(json, s).forEach(::add)
                "string" -> checkString(json.content, s).forEach(::add)
            }
        }

        s.allOf?.forEach { problems += applied(it, refs) }
        s.anyOf?.let { problems += checkAlternatives(value, s, it, "anyOf", at, refs, exemptHere) }
        s.oneOf?.let { problems += checkAlternatives(value, s, it, "oneOf", at, refs, exemptHere) }
        s.not?.let { if (allows(value, it, refs, exemptHere)) add("${value.shown()} matches its 'not' schema") }
        s.ifSchema?.let { condition ->
            val branch = if (allows(value, condition, refs, exemptHere)) s.thenSchema else s.elseSchema
            branch?.let { problems += applied(it, refs) }
        }
        return problems
    }

    private fun checkAlternatives(
        value: Value, schema: Schema.Obj, alternatives: List<Schema>, keyword: String, at: String, refs: Set<String>,
        exempt: Set<String>,
    ): List<String> {
        discriminated(value.json, schema, alternatives)?.let { return check(value, it, at, refs, exempt) }
        val matching = alternatives.count { allows(value, it, refs, exempt) }
        return when {
            matching == 1 || (matching > 1 && keyword == "anyOf") -> emptyList()
            matching > 1 -> listOf(problem(at, "${value.shown()} matches $matching of its oneOf alternatives, not one"))
            else -> {
                // Alternatives of another type are no candidates; if only one is left, its problems say the most.
                val candidates = alternatives.filter { typeAllows(value.json, it, refs) }
                candidates.singleOrNull()?.let { check(value, it, at, refs, exempt) }
                    ?: listOf(problem(at, "${value.shown()} matches none of its $keyword alternatives"))
            }
        }
    }

    /**
     * The alternative the discriminator selects for [value]: by `mapping`, by the name of a component schema, or else by
     * `defaultMapping`; null if the value lacks the property or no alternative is selected.
     */
    private fun discriminated(value: JsonElement, schema: Schema.Obj, alternatives: List<Schema>): Schema? {
        val discriminator = schema.discriminator ?: return null
        val name = discriminator.propertyName ?: return null
        val key = ((value as? JsonObject)?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        fun alternative(target: String?): Schema? {
            val ref = target?.let { if (it.startsWith("#") || '/' in it) it else "$SCHEMAS$it" } ?: return null
            return alternatives.firstOrNull { (it as? Schema.Obj)?.ref == ref }
        }
        return alternative(discriminator.mapping?.get(key) ?: key) ?: alternative(discriminator.defaultMapping)
    }

    /** Whether [schema]'s own `type`, or that of the schema it refers to, allows [value]'s type. */
    private fun typeAllows(value: JsonElement, schema: Schema, refs: Set<String>): Boolean {
        val s = schema as? Schema.Obj ?: return schema == Schema.Bool(true)
        val types = s.declaredTypes
        if (types.isNotEmpty() && types.none { value.hasType(it) }) return false
        val ref = s.ref?.takeIf { it !in refs } ?: return true
        return resolve(ref)?.let { typeAllows(value, it, refs + ref) } ?: true
    }

    /**
     * The properties [this] and the schemas applied with it (its reference, `allOf`, `anyOf`, `oneOf`) define as
     * `readOnly` or `writeOnly`.
     */
    private fun Schema.Obj.readOrWriteOnlyNames(refs: Set<String>): Set<String> {
        val names = properties.orEmpty().filterValues { it.isReadOrWriteOnly(refs) }.keys.toMutableSet()
        ref?.takeIf { it !in refs }?.let { ref -> (resolve(ref) as? Schema.Obj)?.let { names += it.readOrWriteOnlyNames(refs + ref) } }
        compositionMembers.forEach { member -> (member as? Schema.Obj)?.let { names += it.readOrWriteOnlyNames(refs) } }
        return names
    }

    private fun Schema?.isReadOrWriteOnly(refs: Set<String>): Boolean {
        val s = this as? Schema.Obj ?: return false
        if (s.readOnly == true || s.writeOnly == true) return true
        val ref = s.ref?.takeIf { it !in refs } ?: return false
        return resolve(ref).isReadOrWriteOnly(refs + ref)
    }

    private fun checkObject(value: Value, s: Schema.Obj, at: String, refs: Set<String>, exempt: Set<String>): List<String> {
        val json = value.json as JsonObject
        val problems = mutableListOf<String>()
        fun add(message: String) {
            problems += problem(at, message)
        }
        val properties = s.properties.orEmpty()
        s.required?.filter { it !in json && it !in exempt }?.forEach { add("required property '$it' is missing") }
        s.minProperties?.let { if (json.size < it) add("has ${json.size} properties, fewer than minProperties $it") }
        s.maxProperties?.let { if (json.size > it) add("has ${json.size} properties, more than maxProperties $it") }
        val patterns = s.patternProperties.orEmpty().mapNotNull { (pattern, schema) -> pattern.regex()?.let { it to schema } }
        json.keys.forEach { name ->
            val child = value.child(name)
            fun nested(schema: Schema) = check(child, schema, if (at.isEmpty()) name else "$at.$name", emptySet(), emptySet())
            properties[name]?.let { problems += nested(it) }
            val matched = patterns.filter { (regex, _) -> regex.containsMatchIn(name) }
            matched.forEach { (_, schema) -> problems += nested(schema) }
            if (name !in properties && matched.isEmpty()) s.additionalProperties?.let { additional ->
                if (additional == Schema.Bool(false)) add("property '$name' is not allowed")
                else problems += nested(additional)
            }
            s.propertyNames?.let { names ->
                val key = Value(JsonPrimitive(name), value.pointer)
                if (!allows(key, names, emptySet(), emptySet())) add("property name '$name' does not match 'propertyNames'")
            }
        }
        s.dependentRequired?.forEach { (name, needed) ->
            if (name in json) needed.filter { it !in json }.forEach { add("property '$it' is missing, which '$name' requires") }
        }
        s.dependentSchemas?.forEach { (name, schema) -> if (name in json) problems += check(value, schema, at, refs, exempt) }
        return problems
    }

    private fun checkArray(value: Value, s: Schema.Obj, at: String): List<String> {
        val json = value.json as JsonArray
        val problems = mutableListOf<String>()
        fun add(message: String) {
            problems += problem(at, message)
        }
        s.minItems?.let { if (json.size < it) add("has ${json.size} items, fewer than minItems $it") }
        s.maxItems?.let { if (json.size > it) add("has ${json.size} items, more than maxItems $it") }
        if (s.uniqueItems == true && json.map { it.valueKey() }.toSet().size < json.size) add("has duplicate items")
        val prefix = s.prefixItems.orEmpty()
        json.indices.forEach { i ->
            val schema = prefix.getOrNull(i) ?: s.items ?: return@forEach
            problems += check(value.child(i), schema, "$at[$i]", emptySet(), emptySet())
        }
        s.contains?.let { contains ->
            val count = json.indices.count { allows(value.child(it), contains, emptySet(), emptySet()) }
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
        val range = when (s.format?.takeIf { checkFormats }) {
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
        s.format?.takeIf { checkFormats }?.let { format ->
            val valid = formats[format] ?: return@let
            if (!valid(value)) problems += "$shown is not a valid $format"
        }
        return problems
    }

    /** The schema a `$ref` names: a component schema, or a schema inside one; null if unknown. */
    private fun resolve(ref: String): Schema? {
        if (ref in resolved) return resolved[ref]
        return lookUp(ref).also { resolved[ref] = it }
    }

    private fun lookUp(ref: String): Schema? {
        val fragment = runCatching { URI(ref) }.getOrNull()?.takeIf { it.scheme == null && it.path.isNullOrEmpty() }
            ?.fragment ?: return null
        val segments = runCatching { JsonPointer.parse(fragment).segments }.getOrNull() ?: return null
        if (segments.size < 3 || segments[0] != "components" || segments[1] != "schemas") return null
        val component = components[segments[2]] ?: return null
        if (segments.size == 3) return component
        var element: JsonElement = OasDecoder.json.encodeToJsonElement(SchemaSerializer, component)
        for (segment in segments.drop(3)) {
            element = when (element) {
                is JsonObject -> element[segment]
                is JsonArray -> segment.toIntOrNull()?.let { element.getOrNull(it) }
                else -> null
            } ?: return null
        }
        return runCatching { OasDecoder.json.decodeFromJsonElement(SchemaSerializer, element) }.getOrNull()
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
