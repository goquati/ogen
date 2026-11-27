package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.model.Schema

private class TypeKeyword(val name: String, val types: Set<String>, val isSet: (Schema.Obj) -> Boolean)

// In 3.0 an exclusive bound was `minimum` with `exclusiveMinimum: true`; migration made it one `exclusiveMinimum`.
private fun lowerBoundKeywords(is30: Boolean) = listOf(
    TypeKeyword("minimum", numberTypes) { it.minimum != null || (is30 && it.exclusiveMinimum != null) },
    TypeKeyword("maximum", numberTypes) { it.maximum != null || (is30 && it.exclusiveMaximum != null) },
)

private val objectTypes = setOf("object")
private val arrayTypes = setOf("array")
private val stringTypes = setOf("string")

private val typeKeywords = listOf(
    TypeKeyword("properties", objectTypes) { it.properties != null },
    TypeKeyword("additionalProperties", objectTypes) { it.additionalProperties != null },
    TypeKeyword("patternProperties", objectTypes) { it.patternProperties != null },
    TypeKeyword("required", objectTypes) { !it.required.isNullOrEmpty() },
    TypeKeyword("minProperties", objectTypes) { it.minProperties != null },
    TypeKeyword("maxProperties", objectTypes) { it.maxProperties != null },
    TypeKeyword("propertyNames", objectTypes) { it.propertyNames != null },
    TypeKeyword("dependentSchemas", objectTypes) { it.dependentSchemas != null },
    TypeKeyword("dependentRequired", objectTypes) { !it.dependentRequired.isNullOrEmpty() },
    TypeKeyword("unevaluatedProperties", objectTypes) { it.unevaluatedProperties != null },
    TypeKeyword("items", arrayTypes) { it.items != null },
    TypeKeyword("prefixItems", arrayTypes) { it.prefixItems != null },
    TypeKeyword("minItems", arrayTypes) { it.minItems != null },
    TypeKeyword("maxItems", arrayTypes) { it.maxItems != null },
    TypeKeyword("uniqueItems", arrayTypes) { it.uniqueItems != null },
    TypeKeyword("contains", arrayTypes) { it.contains != null },
    TypeKeyword("minContains", arrayTypes) { it.minContains != null },
    TypeKeyword("maxContains", arrayTypes) { it.maxContains != null },
    TypeKeyword("unevaluatedItems", arrayTypes) { it.unevaluatedItems != null },
    TypeKeyword("minLength", stringTypes) { it.minLength != null },
    TypeKeyword("maxLength", stringTypes) { it.maxLength != null },
    TypeKeyword("pattern", stringTypes) { it.pattern != null },
    TypeKeyword("exclusiveMinimum", numberTypes) { it.exclusiveMinimum != null },
    TypeKeyword("exclusiveMaximum", numberTypes) { it.exclusiveMaximum != null },
    TypeKeyword("multipleOf", numberTypes) { it.multipleOf != null },
)

/**
 * Keywords that apply to none of the schema's declared types, such as `maxLength` on an integer: JSON Schema ignores them,
 * so the constraint they look like never holds. Untyped schemas are skipped, since every keyword can apply to them.
 * For a 3.0 spec, messages leave out the `null` type that migration made of `nullable`.
 */
internal fun typeKeywordWarnings(spec: ValidatedSpec): List<String> {
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val declared = node.schema.declaredTypes.let { if (spec.is30) it - "null" else it }
        if (declared.isEmpty()) return@walkSchemas
        val keywords = typeKeywords.toMutableList().apply {
            addAll(indexOfFirst { it.name == "exclusiveMinimum" }, lowerBoundKeywords(spec.is30))
        }
        keywords.filter { it.isSet(node.schema) && it.types.none(declared::contains) }.forEach {
            warnings.add(
                "keyword '${it.name}' in '${node.location}' has no effect on type ${declared.typeList()}; " +
                    "it applies to ${it.types.typeList()}"
            )
        }
    }
    return warnings
}
