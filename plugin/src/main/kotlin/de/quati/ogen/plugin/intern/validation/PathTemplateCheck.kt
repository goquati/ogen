package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI

/**
 * Path templates whose braces don't form variables: an empty `{}`, an unclosed `{`, a `}` without a `{`, or a variable
 * inside a variable. swagger-parser and the path-parameter check only see well-formed `{name}` variables.
 */
internal fun brokenPathTemplateErrors(spec: OpenAPI): List<String> = spec.paths.orEmpty().keys.mapNotNull { path ->
    templateProblem(path)?.let { "path '$path' $it" }
}

private fun templateProblem(path: String): String? {
    var open = -1
    path.forEachIndexed { i, c ->
        when (c) {
            '{' -> if (open >= 0) return "has a variable inside a variable" else open = i
            '}' -> when {
                open < 0 -> return "has a '}' without a '{'"
                path.substring(open + 1, i).isBlank() -> return "has an empty variable"
                else -> open = -1
            }
        }
    }
    return if (open >= 0) "has an unclosed '{'" else null
}
