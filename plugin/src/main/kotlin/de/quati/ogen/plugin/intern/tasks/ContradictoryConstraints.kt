package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.callbacks.Callback
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

internal fun failOnContradictoryConstraints(spec: OpenAPI, logger: Logger) {
    val errors = getContradictoryConstraintErrors(spec)
    if (errors.isNotEmpty()) {
        errors.forEach { logger.error("\t$it") }
        throw GradleException("Found ${errors.size} contradictory constraints.")
    }
}

/**
 * Finds schemas whose bounds leave no valid value: a lower bound above the upper one, or equal bounds with an
 * exclusive side. Branches of `not`, `anyOf` and `oneOf` count too, since an empty branch is a mistake in the spec.
 */
internal fun getContradictoryConstraintErrors(spec: OpenAPI): List<String> {
    val errors = mutableListOf<String>()
    // swagger-parser puts one object into every place that uses a 3.0 `$ref` or path-level parameter.
    val checked = Collections.newSetFromMap(IdentityHashMap<Schema<*>, Boolean>())

    class Bound(val value: BigDecimal, val exclusive: Boolean, keyword: String, suffix: String = "") {
        val description = "$keyword ${value.toPlainString()}$suffix"
    }

    fun Schema<*>.check(location: String, path: String = "", inProperty: Boolean = false) {
        if (!checked.add(this)) return
        val where = when {
            path.isEmpty() -> location
            inProperty -> "$location, property '$path'"
            else -> "$location at '$path'"
        }

        // The tightest bound wins; on equal values an exclusive bound is the tighter one.
        val lower = listOfNotNull(
            // 3.0 marks `minimum` exclusive with a boolean, 3.1 gives `exclusiveMinimum` its own number.
            minimum?.let {
                val exclusive = exclusiveMinimum == true
                Bound(it, exclusive, "minimum", if (exclusive) " (exclusiveMinimum: true)" else "")
            },
            exclusiveMinimumValue?.let { Bound(it, true, "exclusiveMinimum") },
        ).maxWithOrNull(compareBy<Bound> { it.value }.thenBy { it.exclusive })
        val upper = listOfNotNull(
            maximum?.let {
                val exclusive = exclusiveMaximum == true
                Bound(it, exclusive, "maximum", if (exclusive) " (exclusiveMaximum: true)" else "")
            },
            exclusiveMaximumValue?.let { Bound(it, true, "exclusiveMaximum") },
        ).minWithOrNull(compareBy<Bound> { it.value }.thenBy { !it.exclusive })
        if (lower != null && upper != null) {
            val cmp = lower.value.compareTo(upper.value)
            if (cmp > 0)
                errors.add("$where: ${lower.description} is above ${upper.description}")
            else if (cmp == 0 && (lower.exclusive || upper.exclusive))
                errors.add("$where: ${lower.description} equals ${upper.description}")
        }

        fun checkRange(minKeyword: String, min: Int?, maxKeyword: String, max: Int?) {
            if (min != null && max != null && min > max)
                errors.add("$where: $minKeyword $min is above $maxKeyword $max")
        }
        checkRange("minLength", minLength, "maxLength", maxLength)
        checkRange("minItems", minItems, "maxItems", maxItems)
        checkRange("minProperties", minProperties, "maxProperties", maxProperties)

        fun sub(segment: String) = if (path.isEmpty()) segment else "$path.$segment"
        properties?.forEach { (name, prop) -> prop.check(location, sub(name), inProperty = true) }
        patternProperties?.forEach { (pattern, prop) ->
            prop.check(location, sub("patternProperties[$pattern]"), inProperty)
        }
        (additionalProperties as? Schema<*>)?.check(location, sub("additionalProperties"), inProperty)
        items?.check(location, "$path[]", inProperty)
        prefixItems?.forEachIndexed { i, item -> item.check(location, "$path[$i]", inProperty) }
        allOf?.forEachIndexed { i, s -> s.check(location, sub("allOf[$i]"), inProperty) }
        anyOf?.forEachIndexed { i, s -> s.check(location, sub("anyOf[$i]"), inProperty) }
        oneOf?.forEachIndexed { i, s -> s.check(location, sub("oneOf[$i]"), inProperty) }
        not?.check(location, sub("not"), inProperty)
    }

    fun Content.check(location: String) = forEach { (mediaType, media) -> media.schema?.check("$location ($mediaType)") }
    fun Parameter.check(location: String) {
        schema?.check(location)
        content?.check(location)
    }

    fun Header.check(location: String) {
        schema?.check(location)
        content?.check(location)
    }

    fun RequestBody.check(location: String) = content?.check(location)
    fun ApiResponse.check(location: String) {
        content?.check(location)
        headers?.forEach { (name, header) -> header.check("$location header '$name'") }
    }

    fun PathItem.check(label: String, path: String) {
        parameters?.forEach { it.check("$label'$path' parameter '${it.name}'") }
        readOperationsMap().forEach { (method, operation) ->
            val op = "$label'$path:${method.name}'"
            operation.parameters?.forEach { it.check("$op parameter '${it.name}'") }
            operation.requestBody?.check("$op request body")
            operation.responses?.forEach { (code, response) -> response.check("$op response '$code'") }
            operation.callbacks?.forEach { (name, callback) ->
                callback.forEach { (expression, pathItem) -> pathItem.check("$op callback '$name' ", expression) }
            }
        }
    }

    fun Callback.check(label: String) = forEach { (expression, pathItem) -> pathItem.check(label, expression) }

    // Components go first, so that a shared definition is reported under its own name.
    spec.components?.run {
        schemas?.forEach { (name, schema) -> schema.check("schema '$name'") }
        parameters?.forEach { (name, parameter) -> parameter.check("parameter '$name'") }
        requestBodies?.forEach { (name, body) -> body.check("request body '$name'") }
        responses?.forEach { (name, response) -> response.check("response '$name'") }
        headers?.forEach { (name, header) -> header.check("header '$name'") }
        callbacks?.forEach { (name, callback) -> callback.check("callback '$name' ") }
        pathItems?.forEach { (name, pathItem) -> pathItem.check("path item ", name) }
    }
    spec.paths?.forEach { (path, pathItem) -> pathItem.check("", path) }
    spec.webhooks?.forEach { (name, pathItem) -> pathItem.check("webhook ", name) }
    return errors
}
