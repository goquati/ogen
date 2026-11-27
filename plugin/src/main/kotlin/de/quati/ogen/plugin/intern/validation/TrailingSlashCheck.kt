package de.quati.ogen.plugin.intern.validation

/** Path keys ending with a slash; `/` is how the root path is written. */
internal fun trailingSlashWarnings(spec: ValidatedSpec): List<String> = spec.api.paths?.pathItems.orEmpty().keys
    .filter { it != "/" && it.endsWith('/') }
    .map { "path '$it' has a trailing slash" }
