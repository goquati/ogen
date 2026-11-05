package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import org.gradle.api.GradleException
import org.slf4j.Logger
import java.math.BigDecimal
import java.util.Collections
import java.util.IdentityHashMap

internal fun checkEnumValues(spec: OpenAPI, logger: Logger) {
    val errors = getEnumValueErrors(spec)
    if (errors.isEmpty()) return
    errors.forEach { logger.error("\t$it") }
    throw GradleException("Found ${errors.size} enum value errors.")
}

/**
 * Checks enum values against their schema's type and for duplicates, as far as swagger-parser's model shows them:
 * it casts 3.0 values to the type (null if it can't), and types a 3.1 schema without `type` by its first value.
 */
internal fun getEnumValueErrors(spec: OpenAPI): List<String> {
    val isV31 = spec.specVersion == SpecVersion.V31
    val errors = mutableListOf<String>()

    fun Schema<*>.check(root: String, nested: String = "") {
        val where = if (nested.isEmpty()) root else "$root at '$nested'"
        fun child(name: String) = if (nested.isEmpty()) name else "$nested.$name"

        enum?.takeIf { it.isNotEmpty() }?.let { values ->
            val declared = when {
                isV31 -> types.orEmpty()
                // in 3.0 a schema without `type` stays a plain Schema, typed only by inference
                this::class == Schema::class -> emptySet()
                else -> setOfNotNull(type)
            }
            val nullAllowed = if (isV31) "null" in declared else nullable == true
            val valueTypes = declared - "null"
            val castHint = if (!isV31 && type in setOf("integer", "number"))
                " (swagger-parser also reads a value that isn't a valid $type as null)"
            else ""
            val reported = mutableSetOf<Any?>()
            values.forEach { value ->
                val key = value.enumKey()
                when {
                    key in reported -> Unit
                    value == null && !nullAllowed ->
                        errors.add("enum value null in $where isn't allowed: the schema isn't nullable$castHint")

                    value != null && valueTypes.isNotEmpty() && !value.matchesAny(valueTypes) -> errors.add(
                        "enum value ${value.render()} in $where doesn't match the schema's type ${valueTypes.joinToString(" or ")}"
                    )

                    values.count { it.enumKey() == key } > 1 ->
                        errors.add("enum value ${value.render()} in $where is listed more than once" +
                            if (value == null) castHint else "")

                    else -> return@forEach
                }
                reported.add(key)
            }
        }

        properties?.forEach { (name, prop) -> prop.check(root, child(name)) }
        items?.check(root, "$nested[]")
        (additionalProperties as? Schema<*>)?.check(root, child("additionalProperties"))
        allOf?.forEachIndexed { i, it -> it.check(root, child("allOf[$i]")) }
        anyOf?.forEachIndexed { i, it -> it.check(root, child("anyOf[$i]")) }
        oneOf?.forEachIndexed { i, it -> it.check(root, child("oneOf[$i]")) }
        not?.check(root, child("not"))
    }

    fun Content.check(root: String) = forEach { (mediaType, it) -> it.schema?.check("$root ($mediaType)") }
    fun Header.check(root: String) {
        schema?.check(root)
        content?.check(root)
    }

    // swagger-parser puts the same Parameter object in several places: in 3.0, a path-level or referenced one
    val checkedParameters = Collections.newSetFromMap(IdentityHashMap<Parameter, Boolean>())
    fun Parameter.check(root: String) {
        if (!checkedParameters.add(this)) return
        val where = "$root parameter '$name'"
        schema?.check(where)
        content?.check(where)
    }

    fun RequestBody.check(root: String) = content?.check("$root request body")
    fun ApiResponse.check(root: String) {
        content?.check(root)
        headers?.forEach { (name, it) -> it.check("$root header '$name'") }
    }

    spec.paths?.forEach { (path, pathItem) ->
        pathItem.parameters?.forEach { it.check(path) }
        pathItem.readOperationsMap().forEach { (method, operation) ->
            val root = "$method $path"
            operation.parameters?.forEach { it.check(root) }
            operation.requestBody?.check(root)
            operation.responses?.forEach { (status, it) -> it.check("$root response $status") }
        }
    }

    spec.components?.run {
        schemas?.forEach { (name, it) -> it.check("schema '$name'") }
        parameters?.forEach { (name, it) -> it.check("component '$name'") }
        requestBodies?.forEach { (name, it) -> it.check("component '$name'") }
        responses?.forEach { (name, it) -> it.check("response component '$name'") }
        headers?.forEach { (name, it) -> it.check("header component '$name'") }
    }
    return errors
}

/** Equal for values that are equal in JSON: `1` and `1.0`, but not `1` and `"1"`. */
private fun Any?.enumKey(): Any? = when (this) {
    is Number -> BigDecimal(toString()).stripTrailingZeros()
    else -> this
}

private fun Any.matchesAny(types: Set<String>) = when (this) {
    is String -> "string" in types
    is Boolean -> "boolean" in types
    is Number -> "number" in types ||
        ("integer" in types && BigDecimal(toString()).stripTrailingZeros().scale() <= 0)

    else -> true // an object or array value: not checked
}

private fun Any?.render() = if (this is String) "'$this'" else toString()
