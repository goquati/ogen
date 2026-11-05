package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI

// 3xx counts as success, as in Spectral's operation-success-response: a redirect-only endpoint is legitimate.
// Case-insensitive like HttpCode.parse, so the validator accepts the "2xx" keys the generator accepts.
private val successResponseCode = Regex("[23](\\d\\d|XX)", RegexOption.IGNORE_CASE)

/** Operations without a 2xx or 3xx response. */
internal fun successResponseWarnings(spec: OpenAPI): List<String> = spec.operations()
    .filter { node -> node.operation.responses.orEmpty().keys.none(successResponseCode::matches) }
    .map { "operation '${it.location}' has no 2xx or 3xx response" }
