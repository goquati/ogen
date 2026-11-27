package de.quati.ogen.plugin.intern.validation

/** Operations ogen generates no code for: `additionalOperations` of OpenAPI 3.2. */
internal fun unsupportedOperationWarnings(spec: ValidatedSpec): List<String> =
    spec.api.paths?.pathItems.orEmpty().flatMap { (path, item) ->
        item.additionalOperations.orEmpty().keys.map {
            "operation '$path:$it' is not generated: ogen does not support additionalOperations"
        }
    }
