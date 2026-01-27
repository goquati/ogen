package de.quati.ogen.plugin.intern.codegen.endpoint

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.poet.dsl.addAnnotation
import de.quati.kotlin.util.poet.dsl.addClass
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addInterface
import de.quati.kotlin.util.poet.dsl.addParameter
import de.quati.kotlin.util.poet.dsl.addStringArrayMember
import de.quati.kotlin.util.poet.dsl.buildAnnotationSpec
import de.quati.kotlin.util.takeIfNotEmpty
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.getTypeName
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig


context(d: DirectorySyncService, c: CodeGenContext)
internal fun GeneratorConfig.ServerSpringV4.syncEndpoints() = c.spec.paths.groupedByTag.forEach { (tag, endpoints) ->
    val controllerName = tag.prettyName(postfix = this@syncEndpoints.postfix)
    d.sync(fileName = "$controllerName.kt") {
        addInterface(name = controllerName) {
            createController(
                controllerName = controllerName,
                endpoints = endpoints,
            )
        }
    }
    d.sync(fileName = "_utils.kt") {
        addWebFluxConversionConfig()
    }
}

context(c: CodeGenContext, config: GeneratorConfig.ServerSpringV4)
private fun TypeSpec.Builder.createController(
    controllerName: String,
    endpoints: List<Endpoint>,
) {
    val operationContexts = mutableListOf<TypeSpec>()

    addAnnotation(Poet.restController)
    for (endpoint in endpoints) {
        val paramNameResolver = NameConflictResolver()
        val requestBody = endpoint.requestBodyResolved
        val responseBody = endpoint.responseResolved
        addFunction(name = endpoint.operationName.name) {
            addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND)
            val responseType = when (responseBody.successMediaType?.contentType) {
                null -> Unit::class.asClassName()
                is ContentType.Unknown -> Any::class.asClassName()
                is ContentType.Json -> responseBody.schemaSuccessTypeName
            }
            val fullResponseType = Poet.responseEntity.parameterizedBy(responseType)
            returns(fullResponseType)
            addAnnotation(Poet.requestMapping) {
                addMember("method = [%T.%L]", Poet.requestMethod, endpoint.method.name.uppercase())
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
                    type = parameter.schema.getTypeName(isResponse = false)
                        .poet.copy(nullable = !parameter.required),
                ) {
                    addAnnotation(Poet.annotationClassName(parameter.type)) {
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
                    addAnnotation(Poet.requestBody)
                }

            if (config.addOperationContext)
                operationContexts += endpoint.generateOperationContextTypeSpec {
                    addFunction("createResponse") {
                        val responseType = responseType.takeIf { it != Unit::class.asClassName() }
                        if (responseType != null)
                            addParameter("body", responseType)
                        addParameter(
                            "block", LambdaTypeName.get(
                                receiver = Poet.responseEntity.nestedClass("BodyBuilder"),
                                returnType = Unit::class.asClassName(),
                            )
                        ) { defaultValue("{}") }
                        returns(fullResponseType)
                        addCode {
                            add("return %T.status(defaultSuccessStatus).apply(block)", Poet.responseEntity)
                            if (responseType == null)
                                add(".build()")
                            else
                                add(".body(body)")
                        }
                    }
                }
        }
    }

    operationContexts.forEach { addType(it) }
}

context(c: CodeGenContext)
private fun FileSpec.Builder.addWebFluxConversionConfig() = addClass("OgenWebFluxConversionConfig") {
    addAnnotation(Poet.configuration)
    addSuperinterface(Poet.WebFlux.webFluxConfigurer)
    addFunction("addFormatters") {
        addModifiers(KModifier.OVERRIDE)
        addParameter("reg", Poet.formatterRegistry)
        addCode {
            run {
                val type = Type.PrimitiveType.Uuid.poet
                add("reg.addConverter(String::class.java, %T::class.java) { %T.parse(it) }\n", type, type)
            }
            c.enumSchemas.forEach { schema ->
                val type = schema.getTypeName(isResponse = false).poet
                add("reg.addConverter(String::class.java, %T::class.java) { %T.fromSerial(it) }\n", type, type)
            }
        }
    }
}

private object Poet {
    val configuration = buildAnnotationSpec("org.springframework.context.annotation", "Configuration")
    val restController = buildAnnotationSpec("org.springframework.web.bind.annotation", "RestController")
    val formatterRegistry = ClassName("org.springframework.format", "FormatterRegistry")
    val requestMethod = ClassName("org.springframework.web.bind.annotation", "RequestMethod")
    val requestMapping = ClassName("org.springframework.web.bind.annotation", "RequestMapping")
    val requestBody = ClassName("org.springframework.web.bind.annotation", "RequestBody")
    val responseEntity = ClassName("org.springframework.http", "ResponseEntity")
    fun annotationClassName(type: Endpoint.Parameter.Type) = when (type) {
        Endpoint.Parameter.Type.PATH -> "PathVariable"
        Endpoint.Parameter.Type.QUERY -> "RequestParam"
        Endpoint.Parameter.Type.HEADER -> "RequestHeader"
        Endpoint.Parameter.Type.COOKIE -> "CookieValue"
    }.let { ClassName("org.springframework.web.bind.annotation", it) }

    object WebFlux {
        val webFluxConfigurer =
            ClassName("org.springframework.web.reactive.config", "WebFluxConfigurer")
    }
}