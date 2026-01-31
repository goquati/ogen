package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import de.quati.kotlin.util.poet.dsl.buildAnnotationSpec
import de.quati.ogen.plugin.intern.model.Endpoint

internal object Poet {
    val jvmInline = ClassName("kotlin.jvm", "JvmInline")
    val serializer = ClassName("kotlinx.serialization.builtins", "serializer")
    val nullable = ClassName("kotlinx.serialization.builtins", "nullable")
    val kSerializer = ClassName("kotlinx.serialization", "KSerializer")
    val option = ClassName("de.quati.kotlin.util", "Option")
    val mapSerializer = ClassName("kotlinx.serialization.builtins", "MapSerializer")
    val listSerializer = ClassName("kotlinx.serialization.builtins", "ListSerializer")
    val serialDescriptor = ClassName("kotlinx.serialization.descriptors", "SerialDescriptor")
    val serializationException = ClassName("kotlinx.serialization", "SerializationException")
    val experimentalSerializationApi = ClassName("kotlinx.serialization", "ExperimentalSerializationApi")

    fun serializable(with: TypeName? = null) = buildAnnotationSpec(
        packageName = "kotlinx.serialization",
        className = "Serializable",
    ) {
        if (with != null)
            addMember("with = %T::class", with)
    }

    fun serialName(value: String) = buildAnnotationSpec(
        packageName = "kotlinx.serialization",
        className = "SerialName",
    ) {
        addMember("value = %S", value)
    }

    fun jsonClassDiscriminator(value: String) = buildAnnotationSpec(
        packageName = "kotlinx.serialization.json",
        className = "JsonClassDiscriminator",
    ) {
        addMember("%S", value)
    }

    object Ktor {
        object Request {
            val parameter = ClassName("io.ktor.client.request", "parameter")
            val header = ClassName("io.ktor.client.request", "header")
            val cookie = ClassName("io.ktor.client.request", "cookie")
            val url = ClassName("io.ktor.client.request", "url")
            val request = ClassName("io.ktor.client.request", "request")
            val httpRequestBuilder = ClassName("io.ktor.client.request", "HttpRequestBuilder")
            val setBody = ClassName("io.ktor.client.request", "setBody")
        }

        val url = ClassName("io.ktor.http", "Url")
        val httpMethod = ClassName("io.ktor.http", "HttpMethod")
        val contentType = ClassName("io.ktor.http", "ContentType")
        val contentTypeFun = ClassName("io.ktor.http", "contentType")
        val httpResponse = ClassName("io.ktor.client.statement", "HttpResponse")
        val httpStatusCode = ClassName("io.ktor.http", "HttpStatusCode")
        val typeInfo = ClassName("io.ktor.util.reflect", "TypeInfo")
        val typeInfoFun = ClassName("io.ktor.util.reflect", "typeInfo")
        val httpRequestBuilder = ClassName("io.ktor.client.request", "HttpRequestBuilder")
        val takeFrom = ClassName("io.ktor.http", "takeFrom")
        val appendPathSegments = ClassName("io.ktor.http", "appendPathSegments")
        val buildUrl = ClassName("io.ktor.http", "buildUrl")
        val httpClient = ClassName("io.ktor.client", "HttpClient")
        val attributeKey = ClassName("io.ktor.util", "AttributeKey")
    }

    object Spring {
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
}