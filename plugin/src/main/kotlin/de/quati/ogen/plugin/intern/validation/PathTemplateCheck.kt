package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI

/**
 * Path templates whose braces don't form variables: an empty `{}`, an unclosed `{`, a `}` without a `{`, or a variable
 * inside a variable. swagger-parser and the path-parameter check only see well-formed `{name}` variables.
 */
internal fun brokenPathTemplateErrors(spec: OpenAPI): List<String> = spec.paths.orEmpty().keys.mapNotNull { path ->
    templateProblem(path)?.let { "path '$path' $it" }
}

/**
 * Path keys with a query string or a fragment. A path key is only the path: query parameters belong in `parameters` with
 * `in: query`, and a fragment never reaches the server.
 */
internal fun queryOrFragmentInPathErrors(spec: OpenAPI): List<String> = spec.paths.orEmpty().keys.mapNotNull { path ->
    when {
        '?' in path -> "path '$path' contains a query string; query parameters belong in 'parameters' with 'in: query'"
        '#' in path -> "path '$path' contains a fragment, which never reaches the server"
        else -> null
    }
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
