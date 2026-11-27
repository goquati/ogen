package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.model.Schema

/** A schema's types, following `$ref`s; [ref] is the one written here. Empty types mean any type. */
private class Typed(val types: Set<String>, val ref: String?) {
    fun member(index: Int) = "$index (${types.typeList()}${ref?.let { ", from $it" } ?: ""})"
}

/**
 * Compositions no value can match: `allOf` members whose types exclude each other, a schema whose type excludes one of
 * its `allOf` members, and a schema whose type excludes every member of its `oneOf` or `anyOf`. An integer is a number;
 * a schema without a type fits any; 3.0's `nullable` is a `null` type after migration.
 */
internal fun compositionTypeErrors(spec: ValidatedSpec): List<String> {
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        val where = "'${node.location}'"
        val allOf = schema.allOf.orEmpty().map { it.typed(spec) }.withIndex().filter { it.value.types.isNotEmpty() }
        allOf.forEachIndexed { i, first ->
            allOf.drop(i + 1).filterNot { overlap(first.value, it.value) }.forEach {
                errors.add(
                    "allOf members ${first.value.member(first.index)} and ${it.value.member(it.index)} in $where " +
                        "have no type in common"
                )
            }
        }
        val parent = schema.typed(spec)
        if (parent.types.isEmpty()) return@walkSchemas
        val type = "type ${parent.types.typeList()} in $where"
        allOf.filterNot { overlap(parent, it.value) }.forEach {
            errors.add("$type has no type in common with its allOf member ${it.value.member(it.index)}")
        }
        // For 3.0, the walker leaves out the `{type: null}` alternative that migration added for `nullable`.
        for ((keyword, members) in listOf("oneOf" to schema.oneOf, "anyOf" to schema.anyOf)) {
            if (members.isNullOrEmpty()) continue
            if (members.map { it.typed(spec) }.all { it.types.isNotEmpty() && !overlap(parent, it) })
                errors.add("$type has no type in common with any of its $keyword members")
        }
    }
    return errors
}

private fun Schema.typed(spec: ValidatedSpec): Typed {
    var schema = this as? Schema.Obj ?: return Typed(emptySet(), null)
    var ref: String? = null
    val seen = mutableSetOf<String>()
    while (schema.declaredTypes.isEmpty()) {
        // A reference outside components.schemas, or a cycle, counts as untyped.
        val name = schema.ref?.componentName("schemas")?.takeIf(seen::add) ?: break
        if (ref == null) ref = name
        schema = spec.api.components?.schemas?.get(name) as? Schema.Obj ?: break
    }
    return Typed(schema.declaredTypes, ref)
}

private fun overlap(a: Typed, b: Typed) = a.types.any { x -> b.types.any { y -> x == y || setOf(x, y) == numberTypes } }
