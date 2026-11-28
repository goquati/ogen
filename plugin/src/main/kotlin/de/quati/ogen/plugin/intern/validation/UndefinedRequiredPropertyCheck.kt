package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.model.Schema

/**
 * `required` names that the schema does not define. A schema that constrains its parent's value (an `allOf` member,
 * `then`, …) may require what the parent and its other members define (`allOf: [{ $ref: Base }, { required: [name] }]`),
 * and a schema that allows any name (`additionalProperties`, `unevaluatedProperties`, or `patternProperties` without
 * `additionalProperties: false`) may require any. With `additionalProperties: false`, `patternProperties` define the
 * names their patterns match (unless a pattern whose schema is `false` matches them too), matched with the JVM's regex (#121). Schemas under `not`
 * are skipped.
 */
internal fun undefinedRequiredPropertyErrors(spec: ValidatedSpec): List<String> {
    val componentSchemas = spec.api.components?.schemas.orEmpty()

    // `false` allows no name; `true` and any schema allow every name.
    fun Schema?.allowsAnyName() = this != null && this != Schema.Bool(false)

    // Names this schema defines, its reference and allOf/oneOf/anyOf members included; null if it allows any name.
    fun Schema.definedNames(seenRefs: Set<String> = emptySet()): DefinedNames? {
        // A boolean schema defines no names of its own.
        val schema = this as? Schema.Obj ?: return DefinedNames.NONE
        val patterns = schema.patternProperties.orEmpty()
        if (schema.additionalProperties.allowsAnyName() || schema.unevaluatedProperties.allowsAnyName() ||
            (patterns.isNotEmpty() && schema.additionalProperties == null)
        ) return null
        fun regexes(forbidden: Boolean) = patterns.filterValues { (it == Schema.Bool(false)) == forbidden }.keys
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }
        var names = DefinedNames(
            names = schema.properties.orEmpty().keys,
            patterns = regexes(forbidden = false),
            forbiddenPatterns = regexes(forbidden = true),
        )
        schema.ref?.takeIf { it !in seenRefs }?.let { ref ->
            val target = ref.componentName("schemas")?.let { componentSchemas[it] } ?: return null
            names += target.definedNames(seenRefs + ref) ?: return null
        }
        for (member in schema.compositionMembers) names += member.definedNames(seenRefs) ?: return null
        return names
    }

    // The names each schema may require, null if any; a schema applied to its parent's value inherits the parent's.
    fun allowedNames(componentInherited: (String) -> DefinedNames?): List<Pair<SchemaNode, DefinedNames?>> {
        // SchemaNode keeps identity equality, so this map keys by node, in walk order.
        val allowed = LinkedHashMap<SchemaNode, DefinedNames?>()
        spec.walkSchemas { node ->
            if (node.isInsideNot) return@walkSchemas
            val inherited = when {
                node.componentName != null -> componentInherited(node.componentName)
                node.role == SchemaRole.APPLIED -> allowed[node.parent]
                else -> DefinedNames.NONE
            }
            allowed[node] = inherited?.let { names -> node.schema.definedNames()?.let { names + it } }
        }
        return allowed.toList()
    }

    // A component referenced as a composition member may require what the referencing schema defines, as an inline member may.
    val referencedMemberNames = mutableMapOf<String, DefinedNames?>()
    allowedNames(componentInherited = { DefinedNames.NONE }).forEach { (node, allowed) ->
        node.schema.compositionMembers
            .mapNotNull { (it as? Schema.Obj)?.ref?.componentName("schemas") }
            .forEach { name ->
                referencedMemberNames[name] =
                    if (name in referencedMemberNames) referencedMemberNames[name]?.let { names -> allowed?.let { names + it } }
                    else allowed
            }
    }

    return allowedNames(componentInherited = { referencedMemberNames.getOrDefault(it, DefinedNames.NONE) })
        .flatMap { (node, allowed) ->
            if (allowed == null) emptyList()
            else node.schema.required.orEmpty().filter { it !in allowed }
                .map { "required property '$it' in '${node.location}' is not defined in its properties" }
        }
}

/**
 * The names a schema defines: its properties and the names its patterns match, unless a pattern whose schema is `false`
 * matches them too, as all matching patterns apply.
 */
private class DefinedNames(val names: Set<String>, val patterns: List<Regex>, val forbiddenPatterns: List<Regex>) {
    operator fun plus(other: DefinedNames) =
        DefinedNames(names + other.names, patterns + other.patterns, forbiddenPatterns + other.forbiddenPatterns)

    operator fun contains(name: String) = name in names ||
        (patterns.any { it.containsMatchIn(name) } && forbiddenPatterns.none { it.containsMatchIn(name) })

    companion object {
        val NONE = DefinedNames(emptySet(), emptyList(), emptyList())
    }
}
