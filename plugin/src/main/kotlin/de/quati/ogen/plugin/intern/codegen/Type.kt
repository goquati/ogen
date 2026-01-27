package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.TypeWithNullability


context(c: CodeGenContext)
internal fun TypeWithNullability.serializerCreateCode(): CodeBlock = when (val type = type) {
    is Type.NonPrimitiveType.Custom -> when (val s = type.serializer) {
        null -> CodeBlock.of("%T.serializer()", type.poet)
        else -> CodeBlock.of("%T", s)
    }

    is Type.NonPrimitiveType.Flow ->
        throw NotImplementedError("Flow serialization is not supported")

    is Type.NonPrimitiveType.List -> CodeBlock.of(
        "%T(%L)",
        Poet.listSerializer,
        type.innerType.nullable(false).serializerCreateCode(),
    )

    is Type.NonPrimitiveType.Map -> CodeBlock.of(
        "%T(%L, %L)",
        Poet.mapSerializer,
        type.keyType.nullable(false).serializerCreateCode(),
        type.valueType.nullable(false).serializerCreateCode(),
    )

    is Type.NonPrimitiveType.Option -> CodeBlock.of(
        "%T(%L)",
        c.optionSerializer,
        type.innerType.serializerCreateCode(),
    )

    is Type.NonPrimitiveType.SerializableObject -> CodeBlock.of("%T.serializer()", type.poet)
    is Type.PrimitiveType -> when (type) {
        Type.PrimitiveType.Int,
        Type.PrimitiveType.Long,
        Type.PrimitiveType.Float,
        Type.PrimitiveType.Double,
        Type.PrimitiveType.Boolean,
        Type.PrimitiveType.Uuid,
        Type.PrimitiveType.String -> CodeBlock.of("%T.%T()", type.poet, Poet.serializer)

        Type.PrimitiveType.JsonElement,
        Type.PrimitiveType.JsonObject,
        Type.PrimitiveType.JsonNull -> CodeBlock.of("%T.serializer()", type.poet)
    }
}.let {
    if (nullable)
        CodeBlock.of("%L.%T", it, Poet.nullable)
    else
        it
}

context(c: CodeGenContext)
internal fun Type.getSerializerTypeName(register: Boolean): TypeName? = when (this) {
    is Type.NonPrimitiveType.Custom -> serializer
    is Type.NonPrimitiveType.Flow ->
        throw NotImplementedError("Flow serialization is not supported")

    is Type.NonPrimitiveType.List -> run {
        val defaultType = null == innerType.getSerializerTypeName(register = false)
        if (defaultType) return@run null
        c.registerSerializer(
            register = register,
            type = this,
            delegate = this.nullable(false).serializerCreateCode(),
        )
    }

    is Type.NonPrimitiveType.Map -> run {
        val defaultKey = null == keyType.getSerializerTypeName(register = false)
        val defaultValue = null == valueType.getSerializerTypeName(register = false)
        if (defaultKey && defaultValue) return@run null
        c.registerSerializer(
            register = register,
            type = this,
            delegate = this.nullable(false).serializerCreateCode(),
        )
    }

    is Type.NonPrimitiveType.Option -> c.registerSerializer(
        register = register,
        type = this,
        delegate = this.nullable(false).serializerCreateCode(),
    )

    is Type.NonPrimitiveType.SerializableObject -> null // serialized by default

    Type.PrimitiveType.String,
    Type.PrimitiveType.Int,
    Type.PrimitiveType.Long,
    Type.PrimitiveType.Float,
    Type.PrimitiveType.Double,
    Type.PrimitiveType.Boolean,
    Type.PrimitiveType.Uuid,
    Type.PrimitiveType.JsonElement,
    Type.PrimitiveType.JsonObject,
    Type.PrimitiveType.JsonNull -> null // serialized by default
}

internal fun TypeWithNullability.prettyName(capitalized: Boolean = true): String =
    type.prettyName(capitalized = capitalized) + when (nullable) {
        true -> "Nullable"
        false -> ""
    }

internal fun Type.prettyName(capitalized: Boolean = true): String = when (this) {
    is Type.NonPrimitiveType.Custom -> simpleNames.joinToString(separator = "")
    is Type.NonPrimitiveType.Flow -> "Flow${innerType.prettyName(capitalized = true)}"
    is Type.NonPrimitiveType.List -> "List${innerType.prettyName(capitalized = true)}"
    is Type.NonPrimitiveType.Map -> "Map${keyType.prettyName(capitalized = true)}${valueType.prettyName(capitalized = true)}"
    is Type.NonPrimitiveType.Option -> "Option${innerType.prettyName(capitalized = true)}"
    is Type.NonPrimitiveType.SerializableObject -> simpleNames.joinToString(separator = "")
    is Type.PrimitiveType -> this.name
}.let { if (capitalized) it else it.replaceFirstChar(Char::lowercase) }
