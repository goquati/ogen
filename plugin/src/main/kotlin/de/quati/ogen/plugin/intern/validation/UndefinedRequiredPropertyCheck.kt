package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * `required` names that the schema does not define. A schema that constrains its parent's value (an `allOf` member,
 * `then`, …) may require what the parent and its other members define (`allOf: [{ $ref: Base }, { required: [name] }]`),
 * and a schema that allows any name (`additionalProperties`, `patternProperties`, `unevaluatedProperties`) may require
 * any. Schemas under `not` are skipped.
 */
internal fun undefinedRequiredPropertyErrors(spec: OpenAPI): List<String> {
    val componentSchemas = spec.components?.schemas.orEmpty()

    fun Schema<*>?.allowsAnyName() = this != null && booleanSchemaValue != false

    // Names this schema defines, its reference and allOf/oneOf/anyOf members included; null if it allows any name.
    fun Schema<*>.definedNames(seenRefs: Set<String> = emptySet()): Set<String>? {
        if (additionalProperties == true || (additionalProperties as? Schema<*>).allowsAnyName() ||
            unevaluatedProperties.allowsAnyName() || !patternProperties.isNullOrEmpty()
        ) return null
        val names = properties.orEmpty().keys.toMutableSet()
        `$ref`?.takeIf { it !in seenRefs }?.let { ref ->
            val target = ref.componentName("schemas")?.let { componentSchemas[it] } ?: return null
            names += target.definedNames(seenRefs + ref) ?: return null
        }
        for (member in compositionMembers) names += member.definedNames(seenRefs) ?: return null
        return names
    }

    // The names each schema may require, null if any; a schema applied to its parent's value inherits the parent's.
    fun allowedNames(componentInherited: (String) -> Set<String>?): List<Pair<SchemaNode, Set<String>?>> {
        // SchemaNode keeps identity equality, so this map keys by node, in walk order.
        val allowed = LinkedHashMap<SchemaNode, Set<String>?>()
        spec.walkSchemas { node ->
            if (node.isInsideNot) return@walkSchemas
            val inherited = when {
                node.componentName != null -> componentInherited(node.componentName)
                node.role == SchemaRole.APPLIED -> allowed[node.parent]
                else -> emptySet()
            }
            allowed[node] = inherited?.let { names -> node.schema.definedNames()?.let { names + it } }
        }
        return allowed.toList()
    }

    // A component referenced as a composition member may require what the referencing schema defines, as an inline member may.
    val referencedMemberNames = mutableMapOf<String, Set<String>?>()
    allowedNames(componentInherited = { emptySet() }).forEach { (node, allowed) ->
        node.schema.compositionMembers
            .mapNotNull { it.`$ref`?.componentName("schemas") }
            .forEach { name ->
                referencedMemberNames[name] =
                    if (name in referencedMemberNames) referencedMemberNames[name]?.let { names -> allowed?.let { names + it } }
                    else allowed
            }
    }

    return allowedNames(componentInherited = { referencedMemberNames.getOrDefault(it, emptySet()) })
        .flatMap { (node, allowed) ->
            if (allowed == null) emptyList()
            else node.schema.required.orEmpty().filter { it !in allowed }
                .map { "required property '$it' in '${node.location}' is not defined in its properties" }
        }
}
