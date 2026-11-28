package de.quati.ogen.plugin.intern.oas.model

/**
 * Whether the schema has in-place applicators whose evaluated keys depend on the value or a target ogen doesn't
 * follow: `if`/`then`/`else`, `dependentSchemas` or `$dynamicRef`.
 */
internal val Schema.Obj.hasConditionalOrDynamicApplicators: Boolean
    get() = ifSchema != null || thenSchema != null || elseSchema != null || dependentSchemas != null ||
        "\$dynamicRef" in other

/**
 * `additionalProperties`, or `unevaluatedProperties` where it means the same (#130): in an object without in-place
 * applicators (`$ref`, `$dynamicRef`, `allOf`, `anyOf`, `oneOf`, `if`/`then`/`else`, `dependentSchemas`), only
 * `properties` and `patternProperties` evaluate keys, so `unevaluatedProperties` applies to the keys
 * `additionalProperties` would. With applicators, the keys they evaluate depend on their schemas, and
 * `unevaluatedProperties` is ignored; so it is in OpenAPI 3.0 ([is30]), which doesn't know the keyword.
 */
internal fun Schema.Obj.additionalOrUnevaluatedProperties(is30: Boolean): Schema? =
    additionalProperties ?: unevaluatedProperties.takeIf {
        !is30 && ref == null && allOf == null && anyOf == null && oneOf == null && !hasConditionalOrDynamicApplicators
    }
