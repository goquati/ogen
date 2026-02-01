package de.quati.ogen.plugin.intern.codegen.generator

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addCompanionObject
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildClass
import de.quati.kotlin.util.poet.dsl.indent
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.Poet
import de.quati.ogen.plugin.intern.codegen.addConstructorProperty
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig


context(d: DirectorySyncService, _: CodeGenContext)
internal fun GeneratorConfig.KtorClient.Util.sync() {
    d.sync(fileName = "HttpResponseTyped.kt") {
        addType(httpResponseTypedTypeSpec)
    }
    d.sync(fileName = "HttpClientOgen.kt") {
        addType(httpClientOgenTypeSpec)
    }
    d.sync(fileName = "Utils.kt") {
        addKtorClientUtils()
    }
}

context(config: GeneratorConfig.KtorClient.Util)
private val httpResponseTypedTypeSpec: TypeSpec
    get() = buildClass("HttpResponseTyped") {
        val t = TypeVariableName("T", bounds = listOf(Any::class))
        addTypeVariable(t)
        primaryConstructor {
            addParameter("raw", Poet.Ktor.httpResponse)
            addProperty("raw", Poet.Ktor.httpResponse) { initializer("raw") }
            addParameter("bodyTypeInfo", Poet.Ktor.typeInfo)
            addProperty("bodyTypeInfo", Poet.Ktor.typeInfo) { initializer("bodyTypeInfo") }
        }
        addProperty("status", Poet.Ktor.httpStatusCode) { initializer("raw.status") }
        addFunction("body") {
            addModifiers(KModifier.SUSPEND)
            returns(t)
            addCode {
                addStatement("val result = raw.%T<T>(bodyTypeInfo)", Poet.Ktor.Call.body)
                addStatement("return result")
            }
        }

        addCompanionObject {
            addFunction("toTyped") {
                addModifiers(KModifier.INLINE)
                addTypeVariable(t.copy(reified = true))
                receiver(Poet.Ktor.httpResponse)
                returns(config.httpResponseTyped.parameterizedBy(t))
                addCode {
                    add("return %T(\n", config.httpResponseTyped)
                    indent {
                        add("raw = this,\n")
                        add("bodyTypeInfo = %T<T>(),\n", Poet.Ktor.typeInfoFun)
                    }
                    add(")")
                }
            }
        }
    }

context(_: GeneratorConfig.KtorClient.Util)
private val httpClientOgenTypeSpec: TypeSpec
    get() = buildClass("HttpClientOgen") {
        primaryConstructor {
            addConstructorProperty("httpClient", Poet.Ktor.httpClient)
            addConstructorProperty("baseUrl", String::class.asClassName())
            addConstructorProperty(
                "baseModifier",
                LambdaTypeName.get(
                    receiver = Poet.Ktor.httpRequestBuilder,
                    returnType = Unit::class.asClassName(),
                )
            ) { param, _ ->
                param.defaultValue("{}")
            }
        }

        addFunction("buildUrl") {
            returns(Poet.Ktor.url)
            addParameter("path", String::class)
            addParameter("params", Map::class.parameterizedBy(String::class, String::class))
            addCode(
                $$"""
                |return %T {
                |    %T(baseUrl)
                |    val segments = path.trimStart('/').split('/').map { segment ->
                |        var segment = segment
                |        for ((k, v) in params)
                |            segment = segment.replace("{$k}", v)
                |        segment
                |    }
                |    %T(segments, encodeSlash = true)
                |}
            """.trimMargin(), Poet.Ktor.buildUrl, Poet.Ktor.takeFrom, Poet.Ktor.appendPathSegments
            )
        }
    }

context(c: CodeGenContext, _: GeneratorConfig.KtorClient.Util)
private fun FileSpec.Builder.addKtorClientUtils() {
    addProperty(
        "ogenAuthAttr",
        Poet.Ktor.attributeKey.parameterizedBy(
            List::class.asClassName().parameterizedBy(c.specConfig.sharedConfig.securityRequirement),
        ),
    ) {
        initializer("AttributeKey(\"ogenAuth\")")
    }
    addFunction("getOgenAuthNotes") {
        receiver(Poet.Ktor.httpRequestBuilder)
        returns(List::class.asClassName().parameterizedBy(c.specConfig.sharedConfig.securityRequirement))
        addCode("return attributes.getOrNull(ogenAuthAttr) ?: emptyList()")
    }
}
