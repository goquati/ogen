package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.Schema

/** A schema's types, following `$ref`s; [ref] is the one written here. Empty types mean any type. */
private class Typed(val types: Set<String>, val ref: String?) {
    fun member(index: Int) = "$index (${types.typeList()}${ref?.let { ", from $it" } ?: ""})"
}

/**
 * Compositions no value can match: `allOf` members whose types exclude each other, a schema whose type excludes one of
 * its `allOf` members, and a schema whose type excludes every member of its `oneOf` or `anyOf`. An integer is a number;
 * a schema without a type fits any; 3.0's `nullable` adds null.
 */
internal fun compositionTypeErrors(spec: OpenAPI): List<String> {
    val isV31 = spec.specVersion == SpecVersion.V31
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        val where = "'${node.location}'"
        val allOf = schema.allOf.orEmpty().map { it.typed(spec, isV31) }.withIndex().filter { it.value.types.isNotEmpty() }
        allOf.forEachIndexed { i, first ->
            allOf.drop(i + 1).filterNot { overlap(first.value, it.value) }.forEach {
                errors.add(
                    "allOf members ${first.value.member(first.index)} and ${it.value.member(it.index)} in $where " +
                        "have no type in common"
                )
            }
        }
        val parent = schema.typed(spec, isV31)
        if (parent.types.isEmpty()) return@walkSchemas
        val type = "type ${parent.types.typeList()} in $where"
        allOf.filterNot { overlap(parent, it.value) }.forEach {
            errors.add("$type has no type in common with its allOf member ${it.value.member(it.index)}")
        }
        for ((keyword, members) in listOf("oneOf" to schema.oneOf, "anyOf" to schema.anyOf)) {
            if (members.isNullOrEmpty()) continue
            if (members.map { it.typed(spec, isV31) }.all { it.types.isNotEmpty() && !overlap(parent, it) })
                errors.add("$type has no type in common with any of its $keyword members")
        }
    }
    return errors
}

private fun Schema<*>.typed(spec: OpenAPI, isV31: Boolean): Typed {
    var schema: Schema<*> = this
    var ref: String? = null
    val seen = mutableSetOf<String>()
    while (schema.declaredTypes(isV31).isEmpty()) {
        // A reference outside components.schemas, or a cycle, counts as untyped.
        val name = schema.`$ref`?.componentName("schemas")?.takeIf(seen::add) ?: break
        if (ref == null) ref = name
        schema = spec.components?.schemas?.get(name) ?: break
    }
    val declared = schema.declaredTypes(isV31)
    val types = if (!isV31 && declared.isNotEmpty() && schema.nullable == true) declared + "null" else declared
    return Typed(types, ref)
}

private fun overlap(a: Typed, b: Typed) = a.types.any { x -> b.types.any { y -> x == y || setOf(x, y) == numberTypes } }
