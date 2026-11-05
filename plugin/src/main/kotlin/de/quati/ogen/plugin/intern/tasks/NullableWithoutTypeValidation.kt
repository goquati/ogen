package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import org.openapitools.codegen.validation.ValidationResult
import org.openapitools.codegen.validation.ValidationRule
import org.openapitools.codegen.validation.Validated
import java.util.Collections
import java.util.IdentityHashMap

private val nullableWithoutTypeRule = ValidationRule.warn<Schema<*>>(
    "Schema sets 'nullable: true' without a 'type'.",
    "in OpenAPI 3.0 it only takes effect next to a 'type'; a nullable \$ref goes in an 'allOf'",
) { schema ->
    val composed = !schema.allOf.isNullOrEmpty() || !schema.oneOf.isNullOrEmpty() || !schema.anyOf.isNullOrEmpty()
    if (schema.nullable == true && schema.type == null && schema.`$ref` == null && !composed)
        ValidationRule.Fail.empty()
    else
        ValidationRule.Pass.empty()
}

/** Warns about every schema with `nullable: true` but no `type`, `$ref` or composition; OpenAPI 3.0 only. */
internal fun nullableWithoutTypeWarnings(spec: OpenAPI): ValidationResult {
    val result = ValidationResult()
    if (spec.specVersion != SpecVersion.V30) return result
    // The parser's resolver shares one Parameter object between components and every operation using it.
    val visited = Collections.newSetFromMap(IdentityHashMap<Schema<*>, Boolean>())

    fun Schema<*>.check(path: String) {
        if (!visited.add(this)) return
        if (nullableWithoutTypeRule.evaluate(this).failed()) {
            val message = "'nullable: true' without a 'type' in $path: ${nullableWithoutTypeRule.failureMessage}"
            result.addResult(Validated.invalid(nullableWithoutTypeRule, message))
        }
        properties?.forEach { (name, prop) -> prop.check("$path.$name") }
        items?.check("$path[]")
        (additionalProperties as? Schema<*>)?.check("$path.additionalProperties")
        allOf?.forEachIndexed { i, member -> member.check("$path.allOf[$i]") }
        oneOf?.forEachIndexed { i, member -> member.check("$path.oneOf[$i]") }
        anyOf?.forEachIndexed { i, member -> member.check("$path.anyOf[$i]") }
        not?.check("$path.not")
    }

    fun Content.check(path: String) = forEach { (mediaType, media) -> media.schema?.check("$path $mediaType") }
    fun Parameter.check(path: String) {
        schema?.check(path)
        content?.check(path)
    }

    fun Header.check(path: String) {
        schema?.check(path)
        content?.check(path)
    }

    fun RequestBody.check(path: String) = content?.check(path)
    fun ApiResponse.check(path: String) {
        content?.check(path)
        headers?.forEach { (name, header) -> header.check("$path header $name") }
    }

    spec.components?.run {
        schemas?.forEach { (name, schema) -> schema.check(name) }
        parameters?.forEach { (name, parameter) -> parameter.check("components.parameters.$name") }
        requestBodies?.forEach { (name, body) -> body.check("components.requestBodies.$name") }
        responses?.forEach { (name, response) -> response.check("components.responses.$name") }
        headers?.forEach { (name, header) -> header.check("components.headers.$name") }
    }

    spec.paths?.forEach { (path, pathItem) ->
        pathItem.parameters?.forEach { it.check("$path.${it.name}") }
        pathItem.readOperationsMap().forEach { (method, operation) ->
            val location = "$path:${method.name}"
            operation.parameters?.forEach { it.check("$location.${it.name}") }
            operation.requestBody?.check("$location request body")
            operation.responses?.forEach { (status, response) -> response.check("$location response $status") }
        }
    }
    return result
}
