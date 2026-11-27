package de.quati.ogen.plugin.intern.validation

/** Operations without an `operationId`, whose generated method ogen names itself. */
internal fun operationIdRequiredErrors(spec: ValidatedSpec): List<String> = spec.operations()
    .filter { it.operation.operationId.isNullOrBlank() }
    .map { "operation '${it.location}' has no operationId" }
