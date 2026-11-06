package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion

/** Schemas with `nullable: true` but no `type`, `$ref` or composition; OpenAPI 3.0 only, as 3.1 has no `nullable`. */
internal fun nullableWithoutTypeWarnings(spec: OpenAPI): List<String> {
    if (spec.specVersion != SpecVersion.V30) return emptyList()
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        if (schema.nullable == true && schema.type == null && schema.`$ref` == null && schema.compositionMembers.isEmpty())
            warnings.add(
                "'nullable: true' without a 'type' in '${node.location}': it only takes effect next to a 'type'; " +
                    "a nullable \$ref goes in an 'allOf'"
            )
    }
    return warnings
}
