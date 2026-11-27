package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.HttpCode
import de.quati.ogen.plugin.intern.model.HttpMethod
import de.quati.ogen.plugin.intern.model.OperationName
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.oas.model.Operation
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.PathItem
import de.quati.ogen.plugin.intern.oas.model.Paths
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext

context(_: ParserContext)
internal fun Paths.parse() = Spec.Endpoints(
    paths = pathItems.flatMap { (path, pathItem) -> parse(path, pathItem) }
)

/** The operations code is generated for, in swagger-parser's order (`readOperationsMap`). */
internal fun PathItem.operations(): List<Pair<HttpMethod, Operation>> = listOfNotNull(
    get?.let { HttpMethod.GET to it },
    put?.let { HttpMethod.PUT to it },
    post?.let { HttpMethod.POST to it },
    delete?.let { HttpMethod.DELETE to it },
    patch?.let { HttpMethod.PATCH to it },
    head?.let { HttpMethod.HEAD to it },
    options?.let { HttpMethod.OPTIONS to it },
    trace?.let { HttpMethod.TRACE to it },
)

/**
 * The parameters of [operation] as swagger-parser gave them:
 * - those of the Path Item first, unless the operation has one with the same name and location, then the
 *   operation's own (`PathsProcessor.addParametersToEachOperation`);
 * - in 3.0 only, references put in place by the parameter they name (`ParameterProcessor.processParameters`; the
 *   3.1 path of swagger-parser kept them): a local reference to anything but `components/parameters` is dropped,
 *   an unresolvable one kept, and one whose parameter is already there (same name and location) replaces the
 *   first parameter of that name.
 */
context(c: ParserContext)
private fun PathItem.parametersOf(operation: Operation): List<RefOr<Parameter>> {
    // swagger-parser dropped a parameter whose `in` is missing or none of the locations.
    fun List<RefOr<Parameter>>?.readable() = orEmpty().filter { it !is RefOr.Value || it.value.location != null }
    val own = operation.parameters.readable()
    val shared = parameters.readable().filterNot { candidate ->
        candidate is RefOr.Value && own.any {
            it is RefOr.Value && it.value.location == candidate.value.location && it.value.name == candidate.value.name
        }
    }
    val merged = shared + own
    if (c.version != Spec.Version.V3_0) return merged
    fun Parameter.matches(other: RefOr<Parameter>?) =
        other is RefOr.Value && other.value.name == name && other.value.location == location
    val processed = mutableListOf<RefOr<Parameter>>()
    val winning = mutableListOf<Parameter>()
    for (parameter in merged) {
        if (parameter !is RefOr.Ref) {
            processed += parameter
            continue
        }
        if (parameter.ref.startsWith("#") && "#/components/parameters" !in parameter.ref) continue
        val resolved = c.resolveParameter(parameter.ref)
        when {
            resolved == null -> processed += parameter
            processed.any { resolved.matches(it) } || merged.any { resolved.matches(it) } -> winning += resolved
            else -> processed += RefOr.Value(resolved)
        }
    }
    for (resolved in winning) {
        val index = processed.indexOfFirst { it is RefOr.Value && it.value.name == resolved.name }
        if (index >= 0) processed[index] = RefOr.Value(resolved)
    }
    return processed
}

context(s: ParserContext)
private fun parse(path: String, data: PathItem): List<Endpoint> {
    return data.operations().map { (method, operation) ->
        val tag = operation.parseTag()
        // A blank operationId counts as none, as for the validator's operationIdRequired.
        val operationName = operation.operationId
            ?.takeIf { it.isNotBlank() }
            ?.let(OperationName::parse)
            ?: s.generateOperationId(path = path, method = method, tag = tag)
            ?: OperationName.parsePath(path = path, method = method)
        Endpoint(
            method = method,
            path = path,
            tag = tag,
            operationName = operationName,
            deprecated = operation.deprecated ?: false,
            security = operation.security?.parse() ?: s.defaultSecurity,
            summary = operation.summary,
            description = operation.description,
            parameters = data.parametersOf(operation).map { p ->
                val name = (p as? RefOr.Value)?.value?.name?.let { operationName.toParameterSchemaName(it) }
                p.parse(name)
            },
            requestBody = operation.requestBody.parse(name = operationName.toRequestName()),
            responses = operation.responses?.responses?.entries?.associate { (key, value) ->
                val code = HttpCode.parse(key)
                code to value.parse(name = operationName.toResponseName(code))
            } ?: emptyMap(),
        )
    }
}
