package de.quati.ogen.plugin.intern.model

import com.squareup.kotlinpoet.TypeName
import de.quati.kotlin.util.poet.makeDifferent
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
        val schemaName get() = ComponentName.Schema.Root.parse(value.substringAfterLast("/"))

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
        val schemaName get() = Schema.Root.parse(name)

        companion object {
            fun parse(value: String) = Parameter(value.toCamelCase(capitalized = true))
        }
    }

    @JvmInline
    value class RequestBody private constructor(val name: String) : ComponentName {
        override fun toString() = name
        val schemaName get() = Schema.Root.parse(name + "Body")

        companion object {
            fun parse(value: String) = RequestBody(value.toCamelCase(capitalized = true))
        }
    }

    @JvmInline
    value class Response private constructor(val name: String) : ComponentName {
        override fun toString() = name
        val schemaName get() = Schema.Root.parse(name + "Body")

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

    sealed interface Schema : ComponentName {
        val name: String
        val names: List<String>

        operator fun plus(value: String) = Nested.toNested(this, value)
        fun updateLast(block: (String) -> String): Schema

        context(c: CodeGenContext)
        val fileName get() = names.first() + c.schemaPostfix

        context(c: CodeGenContext)
        val prettyClassName get() = names.last() + c.schemaPostfix

        val rawClassName get() = names.last()

        context(c: CodeGenContext)
        val classNameSimpleNames get() = names.map { it + c.schemaPostfix }

        context(c: CodeGenContext)
        val typename get() = c.packageModel.className(classNameSimpleNames)

        fun makeDifferent(blackList: Iterable<Schema>): Schema
        fun toRef(): RefString.Schema?
        fun getParent(): Schema?

        @JvmInline
        value class Root private constructor(override val name: String) : Schema {
            override val names: List<String> get() = listOf(name)
            override fun toString() = name
            override fun plus(value: String) = Nested.toNested(this, value)
            override fun updateLast(block: (String) -> String) = parse(block(name))
            override fun makeDifferent(blackList: Iterable<Schema>) = name.makeDifferent(
                blackList = blackList.map { it.name },
                separator = "",
            ).let(Root::parse)

            override fun toRef() = RefString.Schema.parse("#/components/schemas/$name")
            override fun getParent() = null

            companion object {
                fun parse(value: String) = Root(value.toCamelCase(capitalized = true))
            }
        }

        @JvmInline
        value class Nested private constructor(override val names: List<String>) : Schema {
            override fun toString() = names.joinToString(separator = ".")
            override val name: String get() = names.joinToString(separator = ".")
            override fun updateLast(block: (String) -> String) = Nested(
                names.dropLast(1) + block(names.last())
            )

            override fun makeDifferent(blackList: Iterable<Schema>) = name.makeDifferent(
                blackList = blackList.map { it.name },
                separator = "",
            ).split('.').let(::Nested)

            override fun toRef() = null
            override fun getParent(): Schema = names.dropLast(1).let { parentNames ->
                parentNames.singleOrNull()?.let(Root::parse) ?: Nested(parentNames)
            }

            companion object {
                fun parse(names: List<String>): Schema {
                    require(names.isNotEmpty()) { "names must not be empty" }
                    names.singleOrNull()?.also { return Root.parse(it) }
                    return Nested(names)
                }

                fun toNested(base: Schema, name: String) =
                    Nested(base.names + name.toCamelCase(capitalized = true))
            }
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

