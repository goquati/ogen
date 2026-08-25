package de.quati.ogen.plugin.intern.codegen.generator

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.poet.dsl.addAnnotation
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addInterface
import de.quati.kotlin.util.poet.dsl.addParameter
import de.quati.kotlin.util.poet.dsl.addStringArrayMember
import de.quati.kotlin.util.takeIfNotEmpty
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.Poet
import de.quati.ogen.plugin.intern.codegen.getTypeName
import de.quati.ogen.plugin.intern.codegen.oGenCapitalize
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.HttpCode
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig


context(d: DirectorySyncService, c: CodeGenContext)
internal fun GeneratorConfig.ServerSpringV4.sync() {
    c.spec.paths.groupedByTag.forEach { (tag, endpoints) ->
        val controllerName = tag.prettyName(postfix = this@sync.postfix)
        d.sync(fileName = "$controllerName.kt") {
            addInterface(name = controllerName) {
                createController(
                    controllerName = controllerName,
                    endpoints = endpoints,
                )
            }
        }
    }
    c.globalGenContext.serverSpringV4EnumConversionTypes += c.enumSchemas.map { it.getTypeName(withFlow = false) }
}

private fun getResponseTypeName(
    typeName: TypeName,
    contentType: ContentType?
) = when (contentType) {
    null -> Unit::class.asClassName()
    is ContentType.Unknown -> Any::class.asClassName()
    is ContentType.Json -> typeName
}

private fun TypeName?.toSpringResponseEntity() =
    Poet.Spring.responseEntity.parameterizedBy(this ?: Unit::class.asClassName())

context(c: CodeGenContext, config: GeneratorConfig.ServerSpringV4)
private fun TypeSpec.Builder.createController(
    controllerName: String,
    endpoints: List<Endpoint>,
) {
    val operationContexts = mutableListOf<TypeSpec>()

    addAnnotation(Poet.Spring.restController)
    for (endpoint in endpoints) {
        val paramNameResolver = NameConflictResolver(separator = "")
        val requestBody = endpoint.requestBodyResolved
        val responseBody = endpoint.responseResolved
        addFunction(name = endpoint.operationName.name) {
            addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            val responseType = getResponseTypeName(
                typeName = responseBody.getSchemaSuccessTypeName(withFlow = true),
                contentType = responseBody.successMediaType?.contentType,
            )
            returns(responseType.toSpringResponseEntity())
            addAnnotation(Poet.Spring.requestMapping) {
                addMember("method = [%T.%L]", Poet.Spring.requestMethod, endpoint.method.name.uppercase())
                addMember("value = [%S]", endpoint.path)
                responseBody.successContentType?.values?.takeIfNotEmpty()?.toList()?.also { types ->
                    addStringArrayMember(name = "produces", values = types)
                }
                requestBody?.contentType?.values?.takeIfNotEmpty()?.toList()?.also { types ->
                    addStringArrayMember(name = "consumes", values = types)
                }
            }
            if (endpoint.security.anySecurity && config.contextIfAnySecurity != null)
                addParameter(
                    name = paramNameResolver.resolve("ctx"),
                    type = config.contextIfAnySecurity,
                )
            if (config.addOperationContext)
                addParameter(
                    name = paramNameResolver.resolve("op"),
                    type = config.packageName.className(controllerName, endpoint.operationNameContextName),
                ) {
                    defaultValue(endpoint.operationNameContextName)
                }
            for (parameter in endpoint.parametersContents)
                addParameter(
                    name = paramNameResolver.resolve(parameter.prettyName),
                    type = parameter.schema.getTypeName(withFlow = false)
                        .poet.copy(nullable = !parameter.required),
                ) {
                    addAnnotation(Poet.Spring.annotationClassName(parameter.type)) {
                        addMember("value = %S", parameter.name)
                        addMember("required = %L", parameter.required)
                    }
                }
            if (requestBody != null)
                addParameter(
                    name = paramNameResolver.resolve(requestBody.prettyBodyName),
                    type = when (requestBody.contentType) {
                        null -> Any::class.asClassName()
                        is ContentType.Unknown -> Any::class.asClassName()
                        is ContentType.Json -> requestBody.typeName
                    },
                ) {
                    addAnnotation(Poet.Spring.requestBody)
                }

            if (config.addOperationContext)
                operationContexts += endpoint.generateOperationContextTypeSpec {
                    addCreateResponseFunctions(endpoint = endpoint, controllerResponseTypeName = responseType)
                }
        }
    }

    operationContexts.forEach { addType(it) }
}

context(_: CodeGenContext)
private fun TypeSpec.Builder.addCreateResponseFunctions(
    endpoint: Endpoint,
    controllerResponseTypeName: TypeName,
) {
    endpoint.responses.entries
        .distinctBy { (code, _) -> code.value }
        .forEach { (code, response) ->
            val responseType = response.objOrNull?.content
                ?.firstOrNull() // TODO support multiple content types
                ?.let { content ->
                    getResponseTypeName(
                        typeName = content.schema?.getTypeName(withFlow = true)?.poet ?: Any::class.asClassName(),
                        contentType = content.contentType,
                    )
                }

            addFunction("createResponse${code.value.oGenCapitalize()}") {
                val castRequired = controllerResponseTypeName != responseType
                val statusCodeInt = when (code) {
                    is HttpCode.Explicit -> code.code
                    is HttpCode.Default -> code.defaultCode
                    else -> null
                }
                if (castRequired)
                    addAnnotation(Suppress::class.asClassName()) { addMember("%S", "UNCHECKED_CAST") }
                if (statusCodeInt == null)
                    addParameter("status", Poet.Spring.httpStatusCode)
                if (responseType != null)
                    addParameter("body", responseType)
                addParameter(
                    "block", LambdaTypeName.get(
                        receiver = Poet.Spring.responseEntity.nestedClass("BodyBuilder"),
                        returnType = Unit::class.asClassName(),
                    )
                ) { defaultValue("{}") }
                returns(controllerResponseTypeName.toSpringResponseEntity())
                addCode {
                    add(
                        "return %T.status(%L).apply(block)",
                        Poet.Spring.responseEntity,
                        statusCodeInt ?: "status",
                    )
                    if (responseType == null)
                        add(".build<Unit>()")
                    else
                        add(".body<%T>(body)", responseType)
                    if (castRequired)
                        add(" as %T", controllerResponseTypeName.toSpringResponseEntity())
                }
            }
        }
}
