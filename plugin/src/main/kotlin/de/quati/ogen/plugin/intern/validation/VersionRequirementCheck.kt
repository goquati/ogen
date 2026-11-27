package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.Response

/**
 * Fields the spec's own version requires that OpenAPI 3.2, which the parser reads every spec as, no longer does:
 * `paths`, an operation's `responses` and an array schema's `items` in 3.0, a response's `description` in 3.0 and
 * 3.1. swagger-parser reported them as errors.
 */
internal fun versionRequirementErrors(spec: ValidatedSpec): List<String> {
    val errors = mutableListOf<String>()
    val version = "OpenAPI ${spec.sourceVersion.minor}"
    if (spec.is30 && spec.api.paths == null) errors.add("the spec has no 'paths', which $version requires")
    if (spec.is30) spec.walkSchemas { node ->
        if ("array" in node.schema.declaredTypes && node.schema.items == null)
            errors.add("array schema '${node.location}' has no 'items', which $version requires")
    }
    val operations = spec.operations()
    if (spec.is30) operations.filter { it.operation.responses == null }
        .forEach { errors.add("operation '${it.location}' has no 'responses', which $version requires") }
    if (spec.sourceVersion != OasVersion.V3_2) {
        fun RefOr<Response>.check(location: String) {
            if ((this as? RefOr.Value)?.value?.description == null)
                if (this is RefOr.Value) errors.add("response '$location' has no 'description', which $version requires")
        }
        spec.api.components?.responses?.forEach { (name, response) -> response.check("components.responses.$name") }
        operations.forEach { node ->
            node.operation.responses?.responses?.forEach { (status, response) -> response.check("${node.location}.responses.$status") }
        }
    }
    return errors
}
