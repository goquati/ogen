package de.quati.ogen.plugin.intern.validation

/** Operations ogen generates no code for: the `query` method and `additionalOperations` of OpenAPI 3.2. */
internal fun unsupportedOperationWarnings(spec: ValidatedSpec): List<String> =
    spec.api.paths?.pathItems.orEmpty().flatMap { (path, item) ->
        listOfNotNull(item.query?.let { "operation '$path:QUERY' is not generated: ogen does not support the QUERY method" }) +
            item.additionalOperations.orEmpty().keys.map {
                "operation '$path:$it' is not generated: ogen does not support additionalOperations"
            }
    }
