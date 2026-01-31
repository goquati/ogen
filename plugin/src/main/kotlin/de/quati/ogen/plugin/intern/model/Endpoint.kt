package de.quati.ogen.plugin.intern.model

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.mapValuesNotNull
import de.quati.kotlin.util.poet.toCamelCase
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.ComponentsContext
import de.quati.ogen.plugin.intern.codegen.SchemaTypeSpecData
import de.quati.ogen.plugin.intern.codegen.getTypeName
import de.quati.ogen.plugin.intern.codegen.prettyName
import de.quati.ogen.plugin.intern.codegen.toTypeSpecData
import io.swagger.v3.oas.models.PathItem


internal data class Endpoint(
    val method: PathItem.HttpMethod,
    val path: String,
    val tag: Tag,
    val operationName: OperationName,
    val deprecated: Boolean,
    val security: Security,
    val summary: String?,
    val description: String?,
    val parameters: List<Parameter>,
    val requestBody: RequestBody,
    val responses: Map<HttpCode, Response>,
) {
    val operationNameContextName get() = operationName.name.replaceFirstChar(Char::titlecase) + "Context"

    context(_: CodeGenContext)
    val responseResolved: ResponseResolved
        get() {
            val defaultSuccessStatus = when (responses.keys.size) {
                0 -> 200
                1 -> responses.keys.single().defaultCode
                else -> {
                    val exCodes = responses.keys.filterIsInstance<HttpCode.Explicit>()
                    exCodes.filter { it.isSuccess }.minOfOrNull { it.code }
                        ?: exCodes.firstOrNull()?.code
                        ?: responses.keys.first().defaultCode
                }
            }
            val data = responses.mapValuesNotNull { it.value.objOrNull }
            val successMediaType = data.filterKeys { it.isSuccess }.values.flatMap { it.content }.combine()
            return ResponseResolved(
                data = data,
                successMediaType = successMediaType,
                defaultSuccessStatus = defaultSuccessStatus
            )
        }

    context(_: CodeGenContext)
    val requestBodyResolved
        get() = requestBody.objOrNull?.let {
            val mediaType = it.content.combine()
            RequestBodyResolved(
                data = it,
                mediaType = mediaType,
                type = mediaType?.schema?.getTypeName(isResponse = false)
            )
        }

    context(_: ComponentsContext)
    val parametersContents get() = parameters.map { it.obj }

    data class StringableParameter(
        val name: String,
        val prettyName: String,
        val nullable: Boolean,
        val type: TypeName,
        val inType: Parameter.Type,
        val toStringCodeBlock: CodeBlock,
    )

    sealed interface Parameter {
        data class Ref(val value: RefString.Parameter) : Parameter
        data class Content(
            val name: String,
            val key: ComponentName.Parameter,
            val type: Type,
            val description: String?,
            val required: Boolean,
            val schema: Component.Schema,
        ) : Parameter {
            val prettyName get() = name.toCamelCase(capitalized = false)
        }

        enum class Type { QUERY, PATH, HEADER, COOKIE }

        context(_: ComponentsContext)
        val obj: Content
            get() = when (this) {
                is Content -> this
                is Ref -> value.obj
            }

        context(_: CodeGenContext)
        val toStringableParameter: StringableParameter
            get() {
                val o = obj
                val nullable = !o.required || o.schema.isNullable
                val toStringCodeBlock = o.schema.toStringValueCodeBlock()
                return StringableParameter(
                    name = o.name,
                    prettyName = o.prettyName,
                    nullable = nullable,
                    inType = o.type,
                    type = if (toStringCodeBlock == null) String::class.asClassName()
                    else o.schema.getTypeName(isResponse = false).poet,
                    toStringCodeBlock = toStringCodeBlock ?: CodeBlock.of("toString()")
                )
            }

        companion object {
            context(c: CodeGenContext)
            private fun Component.Schema.toStringValueCodeBlock(followRef: Boolean = true): CodeBlock? = when (this) {
                is Component.Schema.Array, is Component.Schema.Composed, is Component.Schema.MapS,
                Component.Schema.Null, is Component.Schema.Obj, is Component.Schema.SealedInterface,
                is Component.Schema.Unknown -> null

                is Component.Schema.EnumString -> CodeBlock.of("value")
                is Component.Schema.PrimitivType -> CodeBlock.of("toString()")
                is Component.Schema.Ref -> run {
                    if (!followRef) null
                    else when (val typeSpecData = c.refToSchema[this.ref]?.toTypeSpecData()!!) {
                        is SchemaTypeSpecData.DataClass, is SchemaTypeSpecData.SealedInterface -> null
                        is SchemaTypeSpecData.Enum -> CodeBlock.of("value")
                        is SchemaTypeSpecData.ValueClass -> CodeBlock.of(
                            "value.%L",
                            typeSpecData.schema.toStringValueCodeBlock(followRef = false) ?: return@run null,
                        )
                    }
                }
            }
        }
    }

    sealed interface RequestBody {
        data class Ref(val value: RefString.RequestBody) : RequestBody
        data object Empty : RequestBody
        data class Content(
            val key: ComponentName.RequestBody,
            val required: Boolean,
            val description: String?,
            val content: List<ContentMediaType>
        ) : RequestBody

        context(_: ComponentsContext)
        val objOrNull: Content?
            get() = when (this) {
                is Content -> this
                Empty -> null
                is Ref -> value.objOrNull
            }
    }

    class RequestBodyResolved(
        private val data: RequestBody.Content,
        private val mediaType: ContentMediaType?,
        private val type: Type?,
    ) {
        val required get() = data.required
        val description get() = data.description
        val contentType get() = mediaType?.contentType
        val typeName get() = (type?.poet ?: Any::class.asClassName()).copy(nullable = !data.required)
        val prettyBodyName get() = type?.prettyName(capitalized = false) ?: "body"
    }

    class ResponseResolved(
        val data: Map<HttpCode, Response.Content>,
        val successMediaType: ContentMediaType?,
        val defaultSuccessStatus: Int
    ) {
        val successContentType get() = successMediaType?.contentType

        context(_: CodeGenContext)
        val schemaSuccessTypeName
            get() = successMediaType?.schema?.getTypeName(isResponse = true)?.poet
                ?: Any::class.asClassName()
    }

    sealed interface Response {
        data class Ref(val value: RefString.Response) : Response
        data object Empty : Response
        data class Content(
            val key: ComponentName.Response,
            val description: String?,
            val content: List<ContentMediaType>
        ) : Response

        context(_: ComponentsContext)
        val objOrNull: Content?
            get() = when (this) {
                is Content -> this
                Empty -> null
                is Ref -> value.objOrNull
            }
    }
}
