package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.parsing.schemasReachedFromPaths
import kotlinx.serialization.json.JsonPrimitive

/*
 * Checks that replace rules of openapi-generator's OpenApiEvaluator, which the validator used before.
 */

/** The extension that marks a component schema as used on purpose, though no operation refers to it. */
internal const val KEEP_EXTENSION = "x-ogen-keep"

/**
 * Component schemas no operation uses: not reached from any path, directly or through other schemas, and neither
 * a subtype (`allOf` member) of a schema that is nor referred to by such a subtype. A schema with `x-ogen-keep: true`
 * (or `"true"`) counts as used, and so does what it refers to.
 */
internal fun unusedSchemaWarnings(spec: ValidatedSpec): List<String> {
    val schemas = spec.api.components?.schemas.orEmpty()
    // `true` as a boolean or a string: JSON specs often quote the values of extensions.
    val kept = schemas.filterValues { schema ->
        ((schema as? Schema.Obj)?.extensions?.get(KEEP_EXTENSION) as? JsonPrimitive)?.content == "true"
    }
    val used = (spec.api.schemasReachedFromPaths(roots = kept.values, everyKeyword = true) + kept.keys).toMutableSet()
    // A schema that extends a used one through `allOf` is one of its subtypes, and used with it, as is what it refers to.
    do {
        val subtypes = schemas.filterKeys { it !in used }.filterValues { schema ->
            (schema as? Schema.Obj)?.allOf.orEmpty().any { member ->
                (member as? Schema.Obj)?.ref?.componentName("schemas") in used
            }
        }
        used += subtypes.keys
        if (subtypes.isNotEmpty()) used += spec.api.schemasReachedFromPaths(roots = subtypes.values, everyKeyword = true)
    } while (subtypes.isNotEmpty())
    return schemas.keys.filter { it !in used }.map { "schema '$it' is not used by any operation" }
}

/** Schemas with `properties` next to `oneOf`, whose meaning OpenAPI leaves open; other tools may read them differently. */
internal fun oneOfWithPropertiesWarnings(spec: ValidatedSpec): List<String> {
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        if (schema.oneOf != null && !schema.properties.isNullOrEmpty() && "discriminator" !in schema.properties)
            warnings.add("schema '${node.location}' defines properties next to oneOf, which other tools may read differently")
    }
    return warnings
}
