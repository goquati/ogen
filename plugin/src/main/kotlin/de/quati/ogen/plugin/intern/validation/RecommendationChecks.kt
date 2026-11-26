package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.parsing.schemasReachedFromPaths

/*
 * Checks that replace rules of openapi-generator's OpenApiEvaluator, which the validator used before (plan, phase 7).
 */

/**
 * Component schemas no operation uses: not reached from any path, directly or through other schemas, and no
 * subtype (`allOf` member) of a schema that is.
 */
internal fun unusedSchemaWarnings(spec: ValidatedSpec): List<String> {
    val schemas = spec.api.components?.schemas.orEmpty()
    val used = spec.api.schemasReachedFromPaths().toMutableSet()
    // A schema that extends a used one through `allOf` is one of its subtypes, and used with it.
    do {
        val subtypes = schemas.filterKeys { it !in used }.filterValues { schema ->
            (schema as? Schema.Obj)?.allOf.orEmpty().any { member ->
                (member as? Schema.Obj)?.ref?.componentName("schemas") in used
            }
        }.keys
        used += subtypes
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
