package de.quati.ogen.plugin.intern.model

import com.squareup.kotlinpoet.TypeName
import de.quati.kotlin.util.poet.toCamelCase
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.ComponentsContext
import io.swagger.v3.oas.models.PathItem

internal data class TypeWithFormat(
    val type: String,
    val format: String?,
)

internal interface SpecSecurityContext {
    val securityRequirementObjects: Map<String, Security.RequirementObject>
}

internal interface SpecInfoContext : SpecSecurityContext {
    val version: Spec.Version
    val defaultSecurity: Security
}

internal sealed interface ToPoet {
    val poet: TypeName
}

internal data class TypeWithNullability(
    val type: Type,
    val nullable: Boolean,
) : ToPoet {
    override val poet: TypeName get() = type.poet.copy(nullable = nullable)
}

internal sealed interface ContentType {
    val values: Set<String>
    val preferredType: String

    @JvmInline
    value class Json(override val values: Set<String>) : ContentType {
        constructor(value: String) : this(setOf(value))

        override fun toString() = values.toString()
        override val preferredType
            get() = "application/json".takeIf { it in values }
                ?: values.firstOrNull { it.startsWith("application/") && it.endsWith("+json") }
                ?: values.first()
    }

    @JvmInline
    value class Unknown(override val values: Set<String>) : ContentType {
        constructor(value: String) : this(setOf(value))

        override fun toString() = values.toString()
        override val preferredType get() = values.first()
    }

    operator fun plus(other: ContentType) = when {
        this is Json && other is Json -> Json(values + other.values)
        else -> Unknown(values + other.values)
    }

    companion object {
        fun parse(value: String): ContentType {
            val value = value.lowercase()
            if (value == "application/json") return Json(value)
            if (value == "application/ndjson") return Json(value)
            if (value == "application/x-ndjson") return Json(value)
            if (value.startsWith("application/") && value.endsWith("+json"))
                return Json(value)
            return Unknown(value)
        }
    }
}

@JvmInline
internal value class OperationName private constructor(val name: String) {
    override fun toString() = name
    fun toResponseName(code: HttpCode) =
        ComponentName.Response.parse(
            name.replaceFirstChar(Char::titlecase) + "Response" + code.toString()
        )

    fun toRequestName() = ComponentName.RequestBody.parse(name.replaceFirstChar(Char::titlecase) + "Request")
    fun toParameterSchemaName(paramName: String) = ComponentName.Parameter.parse(
        name.replaceFirstChar(Char::titlecase) + "Param" + paramName.toCamelCase(capitalized = true)
    )

    companion object {
        fun parse(value: String) = value.toCamelCase(capitalized = false).let(::OperationName)
        fun parsePath(path: String, method: PathItem.HttpMethod) = path
            .replace("{", "")
            .replace("}", "")
            .replace("/", "_")
            .replace(":", "_")
            .toCamelCase(capitalized = false)
            .let { it + method.name.lowercase().replaceFirstChar(Char::titlecase) }
            .let(::OperationName)
    }
}

internal sealed interface RefString {
    val value: String

    @JvmInline
    value class Schema private constructor(override val value: String) : RefString {
        val schemaName get() = ComponentName.Schema.parse(value.substringAfterLast("/"))

        companion object {
            fun parse(value: String) = Schema(value)
        }
    }

    @JvmInline
    value class Parameter private constructor(override val value: String) : RefString {
        context(c: ComponentsContext)
        val obj: Endpoint.Parameter.Content
            get() = when (val r = c.parameters[name]) {
                is Endpoint.Parameter.Content -> r
                is Endpoint.Parameter.Ref -> r.value.obj
                null -> error("Parameter reference '$this' not found")
            }
        val name get() = ComponentName.Parameter.parse(value.substringAfterLast("/"))

        companion object {
            fun parse(value: String) = Parameter(value)
        }
    }

    @JvmInline
    value class RequestBody private constructor(override val value: String) : RefString {
        context(c: ComponentsContext)
        val objOrNull: Endpoint.RequestBody.Content?
            get() = when (val r = c.requestBody[name]) {
                is Endpoint.RequestBody.Content -> r
                Endpoint.RequestBody.Empty -> null
                is Endpoint.RequestBody.Ref -> r.value.objOrNull
                null -> error("RequestBody reference '$this' not found")
            }
        val name get() = ComponentName.RequestBody.parse(value.substringAfterLast("/"))

        companion object {
            fun parse(value: String) = RequestBody(value)
        }
    }

    @JvmInline
    value class Response private constructor(override val value: String) : RefString {
        context(c: ComponentsContext)
        val objOrNull: Endpoint.Response.Content?
            get() = when (val r = c.response[name]) {
                is Endpoint.Response.Content -> r
                Endpoint.Response.Empty -> null
                is Endpoint.Response.Ref -> r.value.objOrNull
                null -> error("Response reference '$this' not found")
            }
        val name get() = ComponentName.Response.parse(value.substringAfterLast("/"))

        companion object {
            fun parse(value: String) = Response(value)
        }
    }
}

@JvmInline
internal value class Tag private constructor(val name: String) {
    override fun toString() = name
    fun prettyName(postfix: String = "") = name + postfix

    companion object {
        fun parse(value: String) = Tag(value.toCamelCase(capitalized = true))
    }
}

internal sealed interface ComponentName {
    @JvmInline
    value class Parameter private constructor(val name: String) : ComponentName {
        override fun toString() = name
        val schemaName get() = Schema.parse(name)

        companion object {
            fun parse(value: String) = Parameter(value.toCamelCase(capitalized = true))
        }
    }

    @JvmInline
    value class RequestBody private constructor(val name: String) : ComponentName {
        override fun toString() = name
        val schemaName get() = Schema.parse(name + "Body")

        companion object {
            fun parse(value: String) = RequestBody(value.toCamelCase(capitalized = true))
        }
    }

    @JvmInline
    value class Response private constructor(val name: String) : ComponentName {
        override fun toString() = name
        val schemaName get() = Schema.parse(name + "Body")

        companion object {
            fun parse(value: String) = Response(value.toCamelCase(capitalized = true))
        }
    }

    @JvmInline
    value class Security private constructor(val name: String) : ComponentName {
        override fun toString() = name

        companion object {
            fun parse(value: String) = Security(value)
        }
    }

    @JvmInline
    value class Schema private constructor(private val names: List<String>) : ComponentName {
        override fun toString(): String = if (this == Unnamed)
            "Unnamed"
        else
            names.joinToString(separator = ".")

        operator fun plus(name: String) = if (this == Unnamed)
            Unnamed
        else
            Schema(names + name)

        context(c: CodeGenContext)
        val fileName
            get() = names.firstOrNull()?.let { it + c.schemaPostfix }
                ?: error("Unnamed schema has no file name")

        context(c: CodeGenContext)
        val prettyClassName
            get() = names.lastOrNull()?.let { it + c.schemaPostfix }
                ?: error("Unnamed schema has no last name")

        val rawClassName
            get() = names.lastOrNull() ?: error("Unnamed schema has no last name")

        fun updateLast(block: (String) -> String) = if (this == Unnamed)
            error("cannot update unnamed schema")
        else Schema(
            names.dropLast(1) + block(names.last())
        )

        fun toRefOrNull() = names.singleOrNull()?.let { RefString.Schema.parse("#/components/schemas/$it") }

        context(c: CodeGenContext)
        val classNameSimpleNames get() = names.map { it + c.schemaPostfix }

        context(c: CodeGenContext)
        val typename
            get() = if (this == Unnamed)
                error("Unnamed schema has no typename")
            else
                c.packageModel.className(classNameSimpleNames)

        companion object {
            val Unnamed = Schema(emptyList())

            fun parse(value: String) = Schema(listOf(value.toCamelCase(capitalized = true)))
        }
    }
}


internal data class Discriminator(
    val propertyName: String,
    val mapping: Map<String, RefString>?,
)

internal data class ElementDiscriminatorInfo(
    val discriminatorName: String,
    val elementName: String,
    val interfaces: List<Component.Schema.SealedInterface>,
)

