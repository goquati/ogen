package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.parsing.operations

/**
 * Operations under `paths` without an `operationId`, whose generated method ogen names itself. Webhooks and callbacks
 * aren't checked: ogen generates no methods for them.
 */
internal fun operationIdRequiredErrors(spec: ValidatedSpec): List<String> =
    spec.api.paths?.pathItems.orEmpty().flatMap { (path, item) ->
        item.operations()
            .filter { (_, operation) -> operation.operationId.isNullOrBlank() }
            .map { (method, _) -> "operation '$path:${method.name}' has no operationId" }
    }
