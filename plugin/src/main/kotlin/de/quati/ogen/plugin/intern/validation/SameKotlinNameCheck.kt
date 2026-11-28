package de.quati.ogen.plugin.intern.validation

import de.quati.kotlin.util.poet.toCamelCase
import de.quati.ogen.plugin.intern.oas.model.Schema

/**
 * Property names of one schema, from `properties` and `required`, whose Kotlin name is the same, such as `xTenant` and
 * `x-tenant`. The generated class appends `_` to all but one (#129), so adding or reordering such a name can give an
 * existing Kotlin name to another JSON key without breaking the code that uses it. The discriminator of a `oneOf` or
 * `anyOf` that refers to the schema is no property of its class, so it doesn't count.
 */
internal fun sameKotlinNameWarnings(spec: ValidatedSpec): List<String> {
    val discriminators = mutableMapOf<String, MutableSet<String>>()
    spec.walkSchemas { node ->
        val propertyName = node.schema.discriminator?.propertyName ?: return@walkSchemas
        (node.schema.oneOf.orEmpty() + node.schema.anyOf.orEmpty()).forEach { member ->
            val name = (member as? Schema.Obj)?.ref?.componentName("schemas") ?: return@forEach
            discriminators.getOrPut(name, ::mutableSetOf) += propertyName
        }
    }
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val discriminator = node.componentName?.let(discriminators::get).orEmpty()
        val names = (node.schema.properties.orEmpty().keys + node.schema.required.orEmpty()).distinct()
            .filter { it !in discriminator }
        names.groupBy { it.toCamelCase(capitalized = false) }.filterValues { it.size > 1 }
            .forEach { (kotlinName, group) ->
                warnings += "schema '${node.location}' has properties ${group.joinToString { "'$it'" }} " +
                    "with the same Kotlin name '$kotlinName'; all but one get '_' appended"
            }
    }
    return warnings
}
