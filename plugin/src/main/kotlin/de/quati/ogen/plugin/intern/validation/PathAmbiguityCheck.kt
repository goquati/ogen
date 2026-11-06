package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI

private class PathKey(val path: String, val methods: Set<String>) {
    // A trailing slash stays an empty last segment: `/items/` is another path than `/items`.
    val written = path.removePrefix("/").split('/')

    // Each segment with its variables' names blanked: `{id}` and `{userId}` compare equal, and `{id}:cancel` keeps
    // its text.
    val segments = written.map { it.replace(pathVariable, "{}") }

    // Only a whole-segment variable matches any segment; a segment like `{id}:cancel` is compared as it is written.
    fun isVariable(i: Int) = segments[i] == "{}"

    // Identical paths share it.
    val shape = segments.joinToString("/", prefix = "/")
}

private fun OpenAPI.pathKeys() = paths.orEmpty().map { (path, item) ->
    PathKey(path, item.readOperationsMap().keys.map { it.name }.toSet())
}

/** Paths that differ only in their variables' names, which OpenAPI forbids: they match the same requests. */
internal fun identicalPathErrors(spec: OpenAPI): List<String> = spec.pathKeys()
    .groupBy { it.shape }.values
    .filter { it.size > 1 }
    .flatMap { group ->
        val first = group.first().path
        group.drop(1).map { "path '${it.path}' is identical to '$first': only the variable names differ" }
    }

/**
 * Pairs of paths a request can match both of, with no rule to choose: each has a literal segment where the other has a
 * variable (`/{entity}/me` and `/books/{id}` both match `/books/me`), and they share a method. A path whose only
 * difference is a literal where the other has a variable is not ambiguous: OpenAPI prefers the concrete one.
 * A segment that mixes a variable and text (`{id}:cancel`) is compared as written, so an ambiguity it causes with a
 * literal segment (`x:cancel`) goes unreported.
 */
internal fun ambiguousPathWarnings(spec: OpenAPI): List<String> {
    val keys = spec.pathKeys()
    return keys.flatMapIndexed { i, a ->
        keys.drop(i + 1).mapNotNull { b ->
            if (a.segments.size != b.segments.size || a.shape == b.shape) return@mapNotNull null
            val indices = a.segments.indices
            val conflict = indices.any { !a.isVariable(it) && !b.isVariable(it) && a.segments[it] != b.segments[it] }
            val aMoreConcrete = indices.any { !a.isVariable(it) && b.isVariable(it) }
            val bMoreConcrete = indices.any { a.isVariable(it) && !b.isVariable(it) }
            val methods = a.methods intersect b.methods
            if (conflict || !aMoreConcrete || !bMoreConcrete || methods.isEmpty()) return@mapNotNull null
            val request = indices.joinToString("/", prefix = "/") {
                if (a.isVariable(it)) b.written[it] else a.written[it]
            }
            val requestMethods = methods.sorted().joinToString("/")
            "paths '${a.path}' and '${b.path}' are ambiguous: $requestMethods $request matches both"
        }
    }
}
