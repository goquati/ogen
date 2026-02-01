package de.quati.ogen.plugin.intern.codegen.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.kotlin.util.poet.dsl.addClass
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addCompanionObject
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addParameter
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.getter
import de.quati.kotlin.util.poet.dsl.indent
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.kotlin.util.poet.makeDifferent
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.Poet
import de.quati.ogen.plugin.intern.codegen.addConstructorProperty
import de.quati.ogen.plugin.intern.codegen.toParameterMapCodeBlock
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig


context(d: DirectorySyncService, c: CodeGenContext)
internal fun GeneratorConfig.KtorClient.sync() {
    c.spec.paths.groupedByTag.forEach { (tag, endpoints) ->
        val controllerName = tag.prettyName(postfix = this@sync.postfix)
        d.sync(fileName = "$controllerName.kt") {
            addController(controllerName = controllerName, endpoints = endpoints)
        }
    }
}

private data class RequestBodyInfo(
    val name: String,
    val typeInfoName: String?,
    val typeName: TypeName,
    val body: Endpoint.RequestBodyResolved,
)

private data class ResponseBodyInfo(
    val typeName: TypeName,
    val contentType: String?,
)

context(_: CodeGenContext, config: GeneratorConfig.KtorClient)
private fun FileSpec.Builder.addController(
    controllerName: String,
    endpoints: List<Endpoint>,
) = addClass(name = controllerName) {
    val className = config.packageName.className(controllerName)
    primaryConstructor {
        addConstructorProperty("client", config.util.httpClientOgen)
    }
    addCompanionObject {
        addProperty(
            name = controllerName.replaceFirstChar(Char::lowercaseChar),
            type = className
        ) {
            receiver(config.util.httpClientOgen)
            getter {
                addCode("return %T(this)", className)
            }
        }
    }
    for (endpoint in endpoints)
        addEndpoint(endpoint)
}

context(_: CodeGenContext, config: GeneratorConfig.KtorClient)
private fun TypeSpec.Builder.addEndpoint(endpoint: Endpoint) {
    addFunction(name = endpoint.operationName.name) {
        val parameters = endpoint.parameters.map { it.toStringableParameter }
        val reservedNames = parameters.map { it.prettyName }.toSet()
        val blockName = "block".makeDifferent(reservedNames)
        val responseBodyInfo = endpoint.responseResolved.let { responseBody ->
            ResponseBodyInfo(
                typeName = when (responseBody.successMediaType?.contentType) {
                    null -> Unit::class.asClassName()
                    is ContentType.Unknown -> Any::class.asClassName()
                    is ContentType.Json -> responseBody.getSchemaSuccessTypeName(withFlow = false)
                },
                contentType = responseBody.successMediaType?.contentType?.preferredType,
            )
        }
        val requestBodyInfo = endpoint.requestBodyResolved?.let { body ->
            val name = body.prettyBodyName.makeDifferent(reservedNames)
            val type = when (body.contentType) {
                null, is ContentType.Unknown -> null
                is ContentType.Json -> body.typeName
            }
            RequestBodyInfo(
                name = name,
                typeName = type ?: Any::class.asClassName(),
                body = body,
                typeInfoName = "bodyType".takeIf { type == null }?.makeDifferent(reservedNames),
            )
        }
        parameters.forEach { param ->
            addParameter(
                name = param.prettyName,
                type = param.type.copy(nullable = param.nullable),
            ) {
                if (param.nullable) defaultValue("null")
            }
        }
        requestBodyInfo?.also { bodyInfo ->
            addParameter(
                name = bodyInfo.name,
                type = bodyInfo.typeName.copy(nullable = !bodyInfo.body.required)
            ) {
                if (!bodyInfo.body.required) defaultValue("null")
            }
            if (bodyInfo.typeInfoName != null)
                addParameter(
                    name = bodyInfo.typeInfoName,
                    type = Poet.Ktor.typeInfo
                )
        }
        addParameter(
            name = blockName,
            type = LambdaTypeName.get(receiver = Poet.Ktor.httpRequestBuilder, returnType = Unit::class.asClassName())
        ) { defaultValue("{}") }

        addModifiers(KModifier.SUSPEND)
        returns(config.util.httpResponseTyped.parameterizedBy(responseBodyInfo.typeName))
        addCode {
            add("return client.httpClient.%T {\n", Poet.Ktor.Request.request)
            indent {
                addStatement("this.method = %T.%L", Poet.Ktor.httpMethod, endpoint.method.ktorName)
                addStatement(
                    "this.attributes[%T] = %L",
                    config.util.ogenAuthAttr,
                    securityRequirementListCodeBlock(endpoint.security)
                )
                addPath(path = endpoint.path, params = parameters)
                parameters.forEach { param ->
                    addParam(param)
                }
                responseBodyInfo.contentType?.also { contentType ->
                    addStatement("this.%T(%L)", Poet.Ktor.Request.accept, Poet.Ktor.contentTypeCodeBlock(contentType))
                }
                if (requestBodyInfo != null)
                    addRequestBody(requestBodyInfo)
                addStatement("client.baseModifier(this)")
                addStatement("$blockName(this)")
            }
            add("}.%T<%T>()", config.util.toTyped, responseBodyInfo.typeName)
        }
    }
}

context(_: CodeGenContext)
private fun CodeBlock.Builder.addParam(param: Endpoint.StringableParameter) {
    val setterTypeName = when (param.inType) {
        Endpoint.Parameter.Type.QUERY -> Poet.Ktor.Request.parameter
        Endpoint.Parameter.Type.HEADER -> Poet.Ktor.Request.header
        Endpoint.Parameter.Type.COOKIE -> Poet.Ktor.Request.cookie
        Endpoint.Parameter.Type.PATH -> return
    }
    if (param.nullable)
        add("if (%L != null) ", param.prettyName)
    add(
        "this.%T(%S, %L.%L)\n",
        setterTypeName, param.name,
        param.prettyName, param.toStringCodeBlock,
    )
}

context(_: CodeGenContext, _: GeneratorConfig.KtorClient)
private fun CodeBlock.Builder.addPath(path: String, params: List<Endpoint.StringableParameter>) {
    add("client.buildUrl(\n")
    indent {
        add("path = %S,\n", path)
        add("params = %L,\n", params.filter { it.inType == Endpoint.Parameter.Type.PATH }.toParameterMapCodeBlock())
    }
    add(").also(this::%T)\n", Poet.Ktor.Request.url)
}

private fun CodeBlock.Builder.addRequestBody(info: RequestBodyInfo) {
    val contentType = info.body.contentType?.values?.firstOrNull()
    if (contentType != null)
        addStatement("this.%T(%L)", Poet.Ktor.contentTypeFun, Poet.Ktor.contentTypeCodeBlock(contentType))
    addStatement("this.%T(%L)", Poet.Ktor.Request.setBody, buildCodeBlock {
        add(info.name)
        if (info.typeInfoName != null)
            add(", %L", info.typeInfoName)
    })
}
