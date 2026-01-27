package de.quati.ogen.plugin.intern.model

import de.quati.kotlin.util.poet.toCamelCase
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.parser.core.models.SwaggerParseResult

context(c: SpecInfoContext)
private fun io.swagger.v3.oas.models.media.Schema<*>.isNullable(): Boolean = when (c.version) {
    Spec.Version.V3_0 -> nullable ?: false
    Spec.Version.V3_1 -> types?.contains("null") ?: false
}

context(_: SpecInfoContext)
private fun io.swagger.v3.oas.models.media.MediaType.parse(
    contentType: ContentType,
    name: ComponentName.Schema,
): ContentMediaType {
    val schema = schema?.parse(name = name)
    return ContentMediaType(
        contentType = contentType,
        schema = schema,
    )
}

context(_: SpecInfoContext)
private fun io.swagger.v3.oas.models.parameters.RequestBody?.parse(
    name: ComponentName.RequestBody
): Endpoint.RequestBody {
    if (this == null)
        return Endpoint.RequestBody.Empty
    if (`$ref` != null)
        return Endpoint.RequestBody.Ref(RefString.RequestBody.parse(`$ref`!!))
    val content = content ?: return Endpoint.RequestBody.Empty
    return Endpoint.RequestBody.Content(
        key = name,
        required = required ?: false,
        description = description,
        content = content.map { (key, value) ->
            value.parse(
                contentType = ContentType.parse(key!!),
                name = name.schemaName,
            )
        }
    )
}

context(_: SpecInfoContext)
private fun io.swagger.v3.oas.models.responses.ApiResponse.parse(
    name: ComponentName.Response,
): Endpoint.Response {
    if (`$ref` != null)
        return Endpoint.Response.Ref(RefString.Response.parse(`$ref`!!))
    val content = content ?: return Endpoint.Response.Empty
    return Endpoint.Response.Content(
        key = name,
        description = description,
        content = content.map { (key, value) ->
            value.parse(
                name = name.schemaName,
                contentType = ContentType.parse(key!!),
            )
        }
    )
}

context(_: SpecInfoContext)
private fun io.swagger.v3.oas.models.parameters.Parameter.parse(
    name: ComponentName.Parameter,
): Endpoint.Parameter {
    if (`$ref` != null)
        return Endpoint.Parameter.Ref(RefString.Parameter.parse(`$ref`!!))
    val paramName = this@parse.name!!
    val schema = schema.parse(name = name.schemaName)
    val type = Endpoint.Parameter.Type.entries.firstOrNull { it.name.equals(`in`, ignoreCase = true) }
        ?: error("Parameter in-type '$paramName' is invalid")
    return Endpoint.Parameter.Content(
        key = name,
        name = paramName,
        type = type,
        description = description,
        required = (type == Endpoint.Parameter.Type.PATH) || (required ?: false),
        schema = schema,
    )
}

context(s: SpecInfoContext)
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
            }.map { it.parse(operationName.toParameterSchemaName(it.name)) },
            requestBody = operation.requestBody.parse(name = operationName.toRequestName()),
            responses = operation.responses?.entries?.associate { (key, value) ->
                val code = HttpCode.parse(key)
                code to value.parse(name = operationName.toResponseName(code))
            } ?: emptyMap(),
        )
    }
}

context(_: SpecInfoContext)
private fun io.swagger.v3.oas.models.Paths.parse() = Spec.Endpoints(
    paths = flatMap { (path, pathItem) -> parse(path, pathItem) }
)

context(_: SpecInfoContext)
private fun io.swagger.v3.oas.models.Components.parse() = Spec.Components(
    schemas = schemas?.entries?.associate { (name, obj) ->
        val name = ComponentName.Schema.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    parameters = parameters?.entries?.associate { (name, obj) ->
        val name = ComponentName.Parameter.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    response = responses?.entries?.associate { (name, obj) ->
        val name = ComponentName.Response.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    requestBody = requestBodies?.entries?.associate { (name, obj) ->
        val name = ComponentName.RequestBody.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
)

internal fun SwaggerParseResult.parse(): Spec {
    val raw = openAPI!!
    val version = if (isOpenapi31)
        Spec.Version.V3_1
    else
        Spec.Version.V3_0
    val security = raw.security.parse()
    val infoContext = object : SpecInfoContext {
        override val version: Spec.Version = version
        override val defaultSecurity: Security = security
    }
    return with(infoContext) {
        val components = raw.components.parse()
        val paths = raw.paths.parse()
        Spec(
            version = version,
            paths = paths,
            components = components,
            security = security,
        )
    }
}

private fun List<io.swagger.v3.oas.models.security.SecurityRequirement>?.parse() =
    Security(this?.map { it.keys.map(ComponentName.Security::parse).toSet() }?.toSet() ?: emptySet())

internal fun io.swagger.v3.oas.models.media.Schema<*>.isExplicitNullType() =
    (type == "null" || types?.singleOrNull() == "null") && format == null

private fun io.swagger.v3.oas.models.media.Schema<*>.refObjOrNull() = `$ref`?.let(RefString.Schema::parse)
private fun io.swagger.v3.oas.models.media.Schema<*>.parseRefOrNull(name: ComponentName.Schema) = refObjOrNull()?.let {
    Component.Schema.Ref(
        name = name,
        ref = it,
        isNullable = false,
    )
}

private fun io.swagger.v3.oas.models.media.Schema<*>.toTypeWithFormatOrNull() = type?.let {
    TypeWithFormat(type = it, format = format)
}

private fun io.swagger.v3.oas.models.media.Schema<*>.toUnknown(name: ComponentName.Schema) =
    Component.Schema.Unknown(name = name, typeWithFormat = toTypeWithFormatOrNull())

context(_: SpecInfoContext)
internal fun io.swagger.v3.oas.models.media.Schema<*>.parse(name: ComponentName.Schema): Component.Schema = when {
    isExplicitNullType() -> Component.Schema.Null
    `$ref` != null -> parseRefOrNull(name = name)!!
    not != null -> toUnknown(name = name)
    oneOf != null -> run {
        val disc = discriminator?.let {
            it.parse() ?: return@run toUnknown(name = name)
        }
        val isNullable = isNullable() || oneOf!!.any { it.isExplicitNullType() }
        val rawSchemasNonNull = oneOf!!.filterNot { it.isExplicitNullType() }
        if (additionalProperties != null) return@run toUnknown(name = name)
        if (properties?.isNotEmpty() == true) return@run toUnknown(name = name)
        if (disc == null)
            return@run rawSchemasNonNull
                .map { it.parse(name = name) }
                .singleOrNull()
                ?.copySchema { it || isNullable }
                ?: toUnknown(name = name)
        if (disc.mapping != null && disc.mapping.size != rawSchemasNonNull.size)
            return@run toUnknown(name = name)
        val mappingReversed = disc.mapping?.entries?.associate { it.value to it.key }
        val innerSchemas = rawSchemasNonNull.associate { schema ->
            val refItem = schema.refObjOrNull() ?: return@run toUnknown(name = name)
            val itemName = if (mappingReversed == null)
                refItem.schemaName.rawClassName
            else
                mappingReversed[refItem] ?: return@run toUnknown(name = name)
            itemName to schema.parseRefOrNull(name = name + itemName.toCamelCase(capitalized = true))!!
        }

        Component.Schema.SealedInterface(
            isNullable = isNullable,
            schemas = innerSchemas,
            discriminatorName = disc.propertyName,
            name = name,
            typeWithFormat = toTypeWithFormatOrNull()
        )
    }

    anyOf != null || allOf != null -> run {
        if (anyOf != null && allOf != null) return@run toUnknown(name = name)
        val (type, rawSchemas) = anyOf?.let { Component.Schema.Composed.Type.AnyOf to it }
            ?: allOf!!.let { Component.Schema.Composed.Type.AllOf to it }
        val isNullable = isNullable() || rawSchemas.any { it.isExplicitNullType() }
        if (discriminator != null) return@run toUnknown(name = name)
        if (additionalProperties != null) return@run toUnknown(name = name)
        if (properties?.isNotEmpty() == true) return@run toUnknown(name = name) // TODO
        val nonNullSchemas = rawSchemas.filterNot { it.isExplicitNullType() }
        nonNullSchemas.singleOrNull()
            ?.parse(name = name)
            ?.copySchema { it || isNullable }
            ?: Component.Schema.Composed(
                type = type,
                isNullable = isNullable,
                schemas = nonNullSchemas.map { it.parse(name = ComponentName.Schema.Unnamed) },
                name = name,
                typeWithFormat = toTypeWithFormatOrNull(),
            )
    }

    enum != null -> run {
        val isNullable = isNullable()
        if (enum!!.any { it !is String })
            return@run toUnknown(name = name)
        val values = enum!!.map { it as String }
            .filterNot { isNullable && it == "null" }
        Component.Schema.EnumString(
            isNullable = isNullable(),
            values = values,
            name = name,
            typeWithFormat = toTypeWithFormatOrNull(),
        )
    }

    else -> when (val type = types?.singleOrNull() ?: types?.singleOrNull { it != "null" }) {
        "null" -> Component.Schema.Null
        null -> toUnknown(name = name)

        "object" -> run {
            val additionalProps = additionalProperties ?: return@run Component.Schema.Obj(
                isNullable = isNullable(),
                required = required?.toSet() ?: emptySet(),
                properties = properties?.mapValues {
                    it.value.parse(name = name + it.key.toCamelCase(capitalized = true))
                } ?: emptyMap(),
                name = name,
                typeWithFormat = toTypeWithFormatOrNull()
            )
            if (additionalProps == true)
                return@run toUnknown(name = name)
            if (!(properties?.isEmpty() ?: true))
                return@run toUnknown(name = name)
            if (additionalProps !is io.swagger.v3.oas.models.media.Schema<*>)
                return@run toUnknown(name = name)
            Component.Schema.MapS(
                isNullable = isNullable(),
                valueSchema = additionalProps.parse(name = name + "Value"),
                name = name,
                typeWithFormat = toTypeWithFormatOrNull()
            )
        }

        "array" -> run {
            val items = items ?: return@run toUnknown(name = name)
            Component.Schema.Array(
                isNullable = isNullable(),
                items = items.parse(name = name.updateLast { "${it}Item" }),
                name = name,
                typeWithFormat = toTypeWithFormatOrNull()
            )
        }

        else -> run {
            val typeE = Component.Schema.PrimitivType.Type.entries
                .find { it.name.equals(type, ignoreCase = true) }
                ?: return@run Component.Schema.Unknown(
                    name = name,
                    typeWithFormat = toTypeWithFormatOrNull()
                )

            Component.Schema.PrimitivType(
                type = typeE,
                format = format,
                isNullable = isNullable(),
                name = name,
            )
        }
    }
}

internal fun io.swagger.v3.oas.models.media.Discriminator.parse(): Discriminator? {
    return Discriminator(
        propertyName = propertyName ?: return null,
        mapping = mapping?.mapValues { RefString.Schema.parse(it.value ?: return null) },
    )
}

internal fun Component.Schema.flatten(): List<Component.Schema> = listOf(this) + when (this) {
    is Component.Schema.Composed -> listOf()
    is Component.Schema.SealedInterface -> schemas.values.flatMap { it.flatten() }
    is Component.Schema.Array -> items.flatten()
    is Component.Schema.Obj -> properties.values.flatMap { it.flatten() }
    is Component.Schema.MapS -> valueSchema.flatten()
    is Component.Schema.EnumString,
    Component.Schema.Null,
    is Component.Schema.PrimitivType,
    is Component.Schema.Ref,
    is Component.Schema.Unknown -> listOf()

}
