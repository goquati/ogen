package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI

/** Path keys ending with a slash; `/` is how the root path is written. */
internal fun trailingSlashWarnings(spec: OpenAPI): List<String> = spec.paths.orEmpty().keys
    .filter { it != "/" && it.endsWith('/') }
    .map { "path '$it' has a trailing slash" }
