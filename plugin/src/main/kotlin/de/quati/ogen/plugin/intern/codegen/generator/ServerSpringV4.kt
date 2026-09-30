package de.quati.ogen.plugin.intern.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import de.quati.kotlin.util.poet.dsl.addAnnotation
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addInterface
import de.quati.kotlin.util.poet.dsl.addParameter
import de.quati.kotlin.util.poet.dsl.addStringArrayMember
import de.quati.kotlin.util.poet.NameConflictResolver
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
    is ContentType.Unknown, is ContentType.Multipart -> Any::class.asClassName()
    is ContentType.Json -> typeName
}

context(_: CodeGenContext)
private val Endpoint.Part.springTypeName: TypeName
    get() = when (file) {
        Endpoint.Part.File.ONE -> Poet.Spring.WebFlux.filePart
        Endpoint.Part.File.MANY -> List::class.asClassName().parameterizedBy(Poet.Spring.WebFlux.filePart)
        Endpoint.Part.File.NONE -> schema.getTypeName(withFlow = false).poet
    }

private fun TypeName?.toSpringResponseEntity() =
    Poet.Spring.responseEntity.parameterizedBy(this ?: Unit::class.asClassName())

context(c: CodeGenContext, config: GeneratorConfig.ServerSpringV4)
private fun TypeSpec.Builder.createController(
    controllerName: String,
    endpoints: List<Endpoint>,
) {
    val operationContexts = mutableListOf<TypeSpec>()
    val apiResponseTypes = mutableListOf<TypeSpec>()

    addAnnotation(Poet.Spring.restController)
    for (endpoint in endpoints) {
        val paramNameResolver = NameConflictResolver(separator = "")
        val requestBody = endpoint.requestBodyResolved
        val requestBodyParts = requestBody?.parts ?: emptyList()
        val responseBody = endpoint.responseResolved
        addFunction(name = endpoint.operationName.name) {
            addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            val responseType = getResponseTypeName(
                typeName = responseBody.getSchemaSuccessTypeName(withFlow = true),
                contentType = responseBody.successMediaType?.contentType,
            )
            if (config.apiResponse) {
                val apiResponseTypeSpec = endpoint.createApiResponseTypeSpec()
                apiResponseTypes += apiResponseTypeSpec
                returns(config.packageName.className(controllerName, apiResponseTypeSpec.name!!))
            } else
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
            when {
                requestBody == null -> Unit

                requestBodyParts.isNotEmpty() -> requestBodyParts.forEach { part ->
                    addParameter(
                        name = paramNameResolver.resolve(part.prettyName),
                        type = part.springTypeName.copy(nullable = !part.required),
                    ) {
                        addAnnotation(Poet.Spring.requestPart) {
                            addMember("value = %S", part.name)
                            addMember("required = %L", part.required)
                        }
                    }
                }

                else -> addParameter(
                    name = paramNameResolver.resolve(requestBody.prettyBodyName),
                    type = when (requestBody.contentType) {
                        null -> Any::class.asClassName()
                        is ContentType.Unknown, is ContentType.Multipart -> Any::class.asClassName()
                        is ContentType.Json -> requestBody.typeName
                    },
                ) {
                    addAnnotation(Poet.Spring.requestBody)
                }
            }

            if (config.addOperationContext)
                operationContexts += endpoint.generateOperationContextTypeSpec {
                    if (!config.apiResponse)
                        addCreateResponseFunctions(endpoint = endpoint, controllerResponseTypeName = responseType)
                }
        }
    }

    apiResponseTypes.forEach { addType(it) }
    operationContexts.forEach { addType(it) }
}

context(_: CodeGenContext)
private fun Endpoint.Response.Content?.bodyTypeName(): TypeName? = this?.content
    ?.firstOrNull() // TODO support multiple content types
    ?.let { content ->
        getResponseTypeName(
            typeName = content.schema?.getTypeName(withFlow = true)?.poet ?: Any::class.asClassName(),
            contentType = content.contentType,
        )
    }

private val HttpCode.apiResponseName: String
    get() = when (this) {
        is HttpCode.Explicit -> httpStatusNames[code]
            ?.split('_')?.joinToString("") { it.lowercase().oGenCapitalize() }
            ?: "Status$code"

        HttpCode.Information -> "Information"
        HttpCode.Success -> "Success"
        HttpCode.Redirection -> "Redirection"
        HttpCode.ClientError -> "ClientError"
        HttpCode.ServerError -> "ServerError"
        HttpCode.Default -> "Default"
        is HttpCode.Unknown -> "Status" + value.filter { it.isLetterOrDigit() }.oGenCapitalize()
    }

/**
 * Creates the typed response of an endpoint: a single data class if the endpoint defines at most one response,
 * otherwise a sealed interface with one data class per response.
 */
context(_: CodeGenContext)
private fun Endpoint.createApiResponseTypeSpec(): TypeSpec {
    val name = operationName.name.oGenCapitalize() + "Response"
    val responses = responses.entries.distinctBy { (code, _) -> code.value }
    val single = responses.singleOrNull()
    if (responses.isEmpty() || single != null) return buildApiResponseDataClass(
        name = name,
        code = single?.key ?: HttpCode.Explicit(200),
        response = single?.value?.objOrNull,
        superinterface = Poet.Lib.Server.Spring.apiResponse,
    )

    val nameResolver = NameConflictResolver(separator = "")
    return TypeSpec.interfaceBuilder(name)
        .addModifiers(KModifier.SEALED)
        .addSuperinterface(Poet.Lib.Server.Spring.apiResponse)
        .apply {
            val sealedName = ClassName("", name)
            responses.forEach { (code, response) ->
                addType(
                    buildApiResponseDataClass(
                        name = nameResolver.resolve(code.apiResponseName),
                        code = code,
                        response = response.objOrNull,
                        superinterface = sealedName,
                    )
                )
            }
        }
        .build()
}

context(_: CodeGenContext)
private fun buildApiResponseDataClass(
    name: String,
    code: HttpCode,
    response: Endpoint.Response.Content?,
    superinterface: TypeName,
): TypeSpec {
    val bodyType = response.bodyTypeName()?.takeIf { it != Unit::class.asClassName() }
    val statusCode = (code as? HttpCode.Explicit)?.code
    val constructor = FunSpec.constructorBuilder()
    val builder = TypeSpec.classBuilder(name)
        .apply { if (bodyType?.hasValueEquality != false) addModifiers(KModifier.DATA) }
        .addSuperinterface(superinterface)
    response?.description?.takeIf { it.isNotBlank() }?.also { builder.addKdoc("%L", it) }

    fun addConstructorProperty(name: String, type: TypeName, default: CodeBlock? = null) {
        constructor.addParameter(
            ParameterSpec.builder(name, type).apply { if (default != null) defaultValue(default) }.build()
        )
        builder.addProperty(
            PropertySpec.builder(name, type, KModifier.OVERRIDE).initializer("%N", name).build()
        )
    }

    if (statusCode == null)
        addConstructorProperty("status", Poet.Spring.httpStatusCodeInterface)
    else
        builder.addProperty(
            PropertySpec.builder("status", Poet.Spring.httpStatusCodeInterface, KModifier.OVERRIDE)
                .initializer(
                    httpStatusNames[statusCode]
                        ?.takeIf { statusCode !in deprecatedHttpStatusCodes }
                        ?.let { CodeBlock.of("%T.%L", Poet.Spring.httpStatusCode, it) }
                        ?: CodeBlock.of("%T.valueOf(%L)", Poet.Spring.httpStatusCodeInterface, statusCode)
                )
                .build()
        )
    if (bodyType != null)
        addConstructorProperty("body", bodyType)
    val contentTypes = response?.content?.flatMap { it.contentType.values }?.distinct().orEmpty()
    if (bodyType != null && contentTypes.isNotEmpty()) {
        val listType = List::class.asClassName().parameterizedBy(Poet.Spring.mediaType)
        builder.addProperty(
            PropertySpec.builder("contentTypes", listType, KModifier.OVERRIDE)
                .initializer(contentTypes.map { CodeBlock.of("%T.valueOf(%S)", Poet.Spring.mediaType, it) }
                    .joinToCode(prefix = "listOf(", suffix = ")"))
                .build()
        )
    }
    addConstructorProperty(
        name = "headers",
        type = Poet.Spring.httpHeaders,
        default = CodeBlock.of("%T.EMPTY", Poet.Spring.httpHeaders),
    )
    return builder.primaryConstructor(constructor.build()).build()
}

/** streamed (`Flow`) and untyped (`Any`, e.g. binary) bodies only have identity equality, so no data class for them */
private val TypeName.hasValueEquality
    get() = when (this) {
        is ParameterizedTypeName -> rawType != Poet.flow
        else -> copy(nullable = false) != Any::class.asClassName()
    }

/** codes whose `org.springframework.http.HttpStatus` enum entries are deprecated */
private val deprecatedHttpStatusCodes = setOf(102, 418, 509, 510)

/** names of the `org.springframework.http.HttpStatus` enum entries */
private val httpStatusNames = mapOf(
    100 to "CONTINUE",
    101 to "SWITCHING_PROTOCOLS",
    102 to "PROCESSING",
    103 to "EARLY_HINTS",
    200 to "OK",
    201 to "CREATED",
    202 to "ACCEPTED",
    203 to "NON_AUTHORITATIVE_INFORMATION",
    204 to "NO_CONTENT",
    205 to "RESET_CONTENT",
    206 to "PARTIAL_CONTENT",
    207 to "MULTI_STATUS",
    208 to "ALREADY_REPORTED",
    226 to "IM_USED",
    300 to "MULTIPLE_CHOICES",
    301 to "MOVED_PERMANENTLY",
    302 to "FOUND",
    303 to "SEE_OTHER",
    304 to "NOT_MODIFIED",
    307 to "TEMPORARY_REDIRECT",
    308 to "PERMANENT_REDIRECT",
    400 to "BAD_REQUEST",
    401 to "UNAUTHORIZED",
    402 to "PAYMENT_REQUIRED",
    403 to "FORBIDDEN",
    404 to "NOT_FOUND",
    405 to "METHOD_NOT_ALLOWED",
    406 to "NOT_ACCEPTABLE",
    407 to "PROXY_AUTHENTICATION_REQUIRED",
    408 to "REQUEST_TIMEOUT",
    409 to "CONFLICT",
    410 to "GONE",
    411 to "LENGTH_REQUIRED",
    412 to "PRECONDITION_FAILED",
    413 to "CONTENT_TOO_LARGE",
    414 to "URI_TOO_LONG",
    415 to "UNSUPPORTED_MEDIA_TYPE",
    416 to "REQUESTED_RANGE_NOT_SATISFIABLE",
    417 to "EXPECTATION_FAILED",
    418 to "I_AM_A_TEAPOT",
    421 to "MISDIRECTED_REQUEST",
    422 to "UNPROCESSABLE_CONTENT",
    423 to "LOCKED",
    424 to "FAILED_DEPENDENCY",
    425 to "TOO_EARLY",
    426 to "UPGRADE_REQUIRED",
    428 to "PRECONDITION_REQUIRED",
    429 to "TOO_MANY_REQUESTS",
    431 to "REQUEST_HEADER_FIELDS_TOO_LARGE",
    451 to "UNAVAILABLE_FOR_LEGAL_REASONS",
    500 to "INTERNAL_SERVER_ERROR",
    501 to "NOT_IMPLEMENTED",
    502 to "BAD_GATEWAY",
    503 to "SERVICE_UNAVAILABLE",
    504 to "GATEWAY_TIMEOUT",
    505 to "HTTP_VERSION_NOT_SUPPORTED",
    506 to "VARIANT_ALSO_NEGOTIATES",
    507 to "INSUFFICIENT_STORAGE",
    508 to "LOOP_DETECTED",
    509 to "BANDWIDTH_LIMIT_EXCEEDED",
    510 to "NOT_EXTENDED",
    511 to "NETWORK_AUTHENTICATION_REQUIRED",
)

context(_: CodeGenContext)
private fun TypeSpec.Builder.addCreateResponseFunctions(
    endpoint: Endpoint,
    controllerResponseTypeName: TypeName,
) {
    endpoint.responses.entries
        .distinctBy { (code, _) -> code.value }
        .forEach { (code, response) ->
            val responseType = response.objOrNull.bodyTypeName()

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
