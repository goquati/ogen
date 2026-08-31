package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.HttpCode
import de.quati.ogen.plugin.intern.model.OperationName
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.Tag
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import io.swagger.v3.oas.models.PathItem


context(_: ParserContext)
internal fun io.swagger.v3.oas.models.Paths.parse() = Spec.Endpoints(
    paths = flatMap { (path, pathItem) -> parse(path, pathItem) }
)

context(s: ParserContext)
private fun parse(path: String, data: PathItem): List<Endpoint> {
    return data.readOperationsMap().map { (method, operation) ->
        val operationName = operation.operationId
            ?.let(OperationName::parse)
            ?: OperationName.parsePath(path = path, method = method)
        Endpoint(
            method = method,
            path = path,
            tag = Tag.parse(operation.tags?.firstOrNull() ?: "base"),
            operationName = operationName,
            deprecated = operation.deprecated ?: false,
            security = operation.security?.parse() ?: s.defaultSecurity,
            summary = operation.summary,
            description = operation.description,
            parameters = buildList {
                data.parameters?.also(::addAll)
                operation.parameters?.also(::addAll)
            }.map { p ->
                val name = p.name?.let { operationName.toParameterSchemaName(it) }
                p.parse(name)
            },
            requestBody = operation.requestBody.parse(name = operationName.toRequestName()),
            responses = operation.responses?.entries?.associate { (key, value) ->
                val code = HttpCode.parse(key)
                code to value.parse(name = operationName.toResponseName(code))
            } ?: emptyMap(),
        )
    }
}
