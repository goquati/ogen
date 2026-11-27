package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/*
 * Checks of what the spec's files say before migration to OpenAPI 3.2 changed it: keywords of 3.0 that have no
 * effect where they stand, keywords next to a `$ref` that every version ignores, and keywords the version deprecates.
 */

private fun JsonObject.isTrue(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true

/**
 * 3.0 schemas with `nullable: true` but no `type`, `$ref` or composition, and nothing the type is inferred from
 * (`items`, `additionalProperties`, `enum`): `nullable` only takes effect next to a type.
 */
internal fun nullableWithoutTypeWarnings(spec: ValidatedSpec): List<String> {
    if (!spec.is30) return emptyList()
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val source = node.source ?: return@walkSchemas
        val typeOrInference = listOf("type", OasSerialNames.REF_KEY, "allOf", "oneOf", "anyOf", "items", "additionalProperties", "enum")
        if (source.isTrue("nullable") && typeOrInference.none { it in source })
            warnings.add(
                "'nullable: true' without a 'type' in '${node.location}': it only takes effect next to a 'type'; " +
                    "a nullable \$ref goes in an 'allOf'"
            )
    }
    return warnings
}

/** 3.0 schemas with `exclusiveMinimum: true` but no `minimum`, or `exclusiveMaximum: true` but no `maximum`. */
internal fun exclusiveFlagWithoutBoundWarnings(spec: ValidatedSpec): List<String> {
    if (!spec.is30) return emptyList()
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val source = node.source ?: return@walkSchemas
        listOf("exclusiveMinimum" to "minimum", "exclusiveMaximum" to "maximum").forEach { (flag, bound) ->
            if (source.isTrue(flag) && bound !in source)
                warnings.add("'$flag: true' in '${node.location}' has no effect without '$bound'")
        }
    }
    return warnings
}

// Keywords that only describe a schema; next to a 3.0 `$ref` they are ignored, which is harmless.
private val annotations = setOf(
    "title", "description", "summary", "example", "examples", "default", "deprecated", "readOnly", "writeOnly",
    "externalDocs", "xml",
)

private fun keywordList(keywords: List<String>) =
    keywords.joinToString { "'$it'" } + if (keywords.size == 1) " is" else " are"

/**
 * Keywords next to a `$ref` that are ignored: in a 3.0 schema everything but annotations (3.1 schemas allow them),
 * and in a Reference Object of a parameter, response and the like everything but `summary` and `description`.
 */
internal fun ignoredNextToRefWarnings(spec: ValidatedSpec): List<String> {
    val warnings = mutableListOf<String>()
    fun JsonObject.ignored(allowed: Set<String>) =
        keys.filter { it != OasSerialNames.REF_KEY && it !in allowed && !OasSerialNames.isExtension(it) }
    if (spec.is30) spec.walkSchemas { node ->
        val source = node.source?.takeIf { OasSerialNames.REF_KEY in it } ?: return@walkSchemas
        val ignored = source.ignored(annotations).takeIf { it.isNotEmpty() } ?: return@walkSchemas
        warnings.add(
            "${keywordList(ignored)} ignored next to '\$ref' in '${node.location}' in OpenAPI 3.0" +
                if ("nullable" in ignored) "; a nullable \$ref goes in an 'allOf'" else ""
        )
    }
    spec.references().forEach { node ->
        val ignored = node.source?.ignored(setOf("summary", "description"))?.takeIf { it.isNotEmpty() } ?: return@forEach
        warnings.add("${keywordList(ignored)} ignored next to '\$ref' in '${node.location}'")
    }
    return warnings
}

/** 3.1 and later have no `nullable`; ogen ignores it there, so the value is not nullable. */
internal fun nullableIn31Warnings(spec: ValidatedSpec): List<String> {
    if (spec.is30) return emptyList()
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        if ("nullable" in node.schema.other)
            warnings.add("'nullable' in '${node.location}' has no effect since OpenAPI 3.1; add 'null' to its 'type' instead")
    }
    return warnings
}

/**
 * A keyword of a schema or its XML Object that OpenAPI deprecates since [since]; [advice] says what to do instead,
 * given the keyword's value.
 */
private class DeprecatedKeyword(val path: List<String>, val since: OasVersion, val advice: (JsonElement) -> String)

// `attribute: false` and `wrapped: false` are the defaults, which need no `nodeType`.
private fun JsonElement.isTrue() = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true

private val deprecatedKeywords = listOf(
    DeprecatedKeyword(listOf("example"), OasVersion.V3_1) { "use 'examples' instead" },
    DeprecatedKeyword(listOf("xml", "attribute"), OasVersion.V3_2) {
        if (it.isTrue()) "use 'nodeType: attribute' instead" else "remove it, 'false' is the default"
    },
    DeprecatedKeyword(listOf("xml", "wrapped"), OasVersion.V3_2) {
        if (it.isTrue()) "use 'nodeType: element' instead" else "remove it, 'false' is the default"
    },
)

/** Schema keywords the spec's OpenAPI version deprecates, such as `example` since 3.1. */
internal fun deprecatedKeywordWarnings(spec: ValidatedSpec): List<String> {
    val deprecated = deprecatedKeywords.filter { spec.sourceVersion >= it.since }
    if (deprecated.isEmpty()) return emptyList()
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        val source = node.source ?: return@walkSchemas
        deprecated.forEach { keyword ->
            val owner = keyword.path.dropLast(1).fold(source as JsonObject?) { obj, key -> obj?.get(key) as? JsonObject }
            val value = owner?.get(keyword.path.last()) ?: return@forEach
            warnings.add(
                "'${keyword.path.joinToString(".")}' in '${node.location}' is deprecated since OpenAPI " +
                    "${keyword.since.minor}; ${keyword.advice(value)}"
            )
        }
    }
    return warnings
}

/** The types as the file writes them: a string or a list. */
internal fun JsonElement.writtenTypes(): List<String>? = when (this) {
    is JsonPrimitive -> listOf(content)
    is kotlinx.serialization.json.JsonArray -> mapNotNull { (it as? JsonPrimitive)?.content }
    else -> null
}
