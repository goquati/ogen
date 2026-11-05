package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.Parameter
import org.gradle.api.GradleException
import org.slf4j.Logger

private val pathTemplateVariable = Regex("""\{([^{}]+)}""")

internal fun checkPathParameters(spec: OpenAPI, logger: Logger) {
    val errors = getPathParameterErrors(spec)
    if (errors.isEmpty()) return
    errors.forEach { logger.error("\t$it") }
    throw GradleException("Found ${errors.size} path parameter errors.")
}

/**
 * Path parameters whose name has no `{name}` in the path template; swagger-parser reports the opposite direction.
 * For OpenAPI 3.0 (not 3.1) the parser has already resolved parameter `$ref`s and copied path-level parameters
 * into each operation, so those are reported once per operation.
 */
internal fun getPathParameterErrors(spec: OpenAPI): List<String> = spec.paths.orEmpty().flatMap { (path, pathItem) ->
    val templateNames = pathTemplateVariable.findAll(path).map { it.groupValues[1] }.toSet()
    // A `$ref` that doesn't resolve is left to swagger-parser, which reports it.
    fun Parameter.resolve(): Parameter? = when (val ref = `$ref`) {
        null -> this
        else -> spec.components?.parameters?.get(ref.substringAfterLast('/'))
    }

    fun List<Parameter>?.errors(location: String) = orEmpty()
        .mapNotNull { it.resolve() }
        .filter { it.`in` == "path" && it.name !in templateNames }
        .map { "path parameter '${it.name}' in $location has no '{${it.name}}' in the path" }

    pathItem.parameters.errors(location = "'$path' (path level)") +
        pathItem.readOperationsMap().flatMap { (method, operation) ->
            operation.parameters.errors(location = "'$path:${method.name}'")
        }
}
