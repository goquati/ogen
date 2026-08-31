package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addCompanionObject
import de.quati.kotlin.util.poet.dsl.addEnumConstant
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addParameter
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildObject
import de.quati.kotlin.util.poet.dsl.buildValueClass
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.kotlin.util.poet.toCamelCase
import de.quati.kotlin.util.poet.toSnakeCase
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.Type

context(c: CodeGenContext)
private fun Component.Schema.getMappingOrNull(): Type.NonPrimitiveType.Custom? {
    if (this is Component.Schema.Ref) {
        c.schemaMappings[name]?.also { return it }
        return requireNotNull(c.allSchemas[name]).getMappingOrNull()
    }
    when (this) {
        is Component.Schema.Inline -> Unit
        is Component.Schema.NonInline -> c.schemaMappings[name]?.also { return it }
    }
    c.typeMappings[typeWithFormat]?.also { return it }
    return null
}

context(c: CodeGenContext)
internal fun Component.Schema.getTypeName(
    withFlow: Boolean,
): Type {
    getMappingOrNull()?.also { return it }
    return when (this) {
        is Component.Schema.NonInline -> Type.NonPrimitiveType.SerializableObject(
            packageName = c.packageModel,
            simpleNames = name.classNameSimpleNames
        )

        is Component.Schema.Inline -> when (this) {
            is Component.Schema.Array.Inline -> items.getTypeName(withFlow = false).let { itemTypeData ->
                when (withFlow) {
                    true -> Type.NonPrimitiveType.Flow(itemTypeData)
                    false -> Type.NonPrimitiveType.List(itemTypeData)
                }
            }

            is Component.Schema.MapS.Inline -> Type.NonPrimitiveType.Map(
                keyType = Type.PrimitiveType.String,
                valueType = valueSchema.getTypeName(withFlow = withFlow),
            )

            is Component.Schema.Primitiv.Inline -> when (type) {
                Component.Schema.Primitiv.Type.STRING -> when (typeWithFormat.format) {
                    "uuid" -> Type.PrimitiveType.Uuid
                    else -> Type.PrimitiveType.String
                }

                Component.Schema.Primitiv.Type.INTEGER -> when (format) {
                    "int32" -> Type.PrimitiveType.Int
                    "int64" -> Type.PrimitiveType.Long
                    else -> Type.PrimitiveType.Int
                }

                Component.Schema.Primitiv.Type.NUMBER -> when (format) {
                    "float" -> Type.PrimitiveType.Float
                    "double" -> Type.PrimitiveType.Double
                    else -> Type.PrimitiveType.Double
                }

                Component.Schema.Primitiv.Type.BOOLEAN -> Type.PrimitiveType.Boolean
            }

            is Component.Schema.Ref -> Type.NonPrimitiveType.SerializableObject(
                packageName = c.packageModel,
                simpleNames = name.classNameSimpleNames
            )

            is Component.Schema.Unknown.Inline -> Type.PrimitiveType.JsonElement
            Component.Schema.Null -> Type.PrimitiveType.JsonNull
        }
    }
}

context(c: CodeGenContext)
private fun Component.Schema.Composed.toObjOrNull(): Component.Schema.Obj? {
    val innerSchemas = schemas.map { schema ->
        when (schema) {
            is Component.Schema.Ref -> when (val refSchema = c.allSchemas[schema.name]) {
                is Component.Schema.Obj -> refSchema
                is Component.Schema.Composed -> refSchema.toObjOrNull()
                else -> null
            }

            else -> null
        } ?: return null
    }
    val allPropNames = innerSchemas.flatMap { it.properties.keys }
    if (allPropNames.size != allPropNames.distinct().size)
        return null // TODO
    return Component.Schema.Obj(
        required = when (type) {
            Component.Schema.Composed.Type.AllOf -> innerSchemas.flatMap { it.required }.toSet()
            Component.Schema.Composed.Type.AnyOf -> emptySet()
        },
        properties = innerSchemas.flatMap { it.properties.entries }
            .associate { it.key to it.value },
        name = name,
        isNullable = isNullable,
        typeWithFormat = typeWithFormat,
    )
}

internal sealed interface SchemaTypeSpecData {
    val schema: Component.Schema

    data class Enum(override val schema: Component.Schema.EnumString) : SchemaTypeSpecData
    data class SealedInterface(override val schema: Component.Schema.SealedInterface) : SchemaTypeSpecData
    data class DataClass(override val schema: Component.Schema.Obj) : SchemaTypeSpecData
    data class ValueClass(override val schema: Component.Schema.Value) : SchemaTypeSpecData
}

context(_: CodeGenContext)
internal fun SchemaTypeSpecData.toTypeSpec(): TypeSpec? = when (this) {
    is SchemaTypeSpecData.Enum -> schema.generateEnumTypeSpec()
    is SchemaTypeSpecData.SealedInterface -> schema.generateSealedInterfaceTypeSpec()
    is SchemaTypeSpecData.DataClass -> schema.generateDataClassTypeSpec()
    is SchemaTypeSpecData.ValueClass -> schema.generateValueClassTypeSpec()
}

context(c: CodeGenContext)
internal fun Component.Schema.NonInline.toTypeSpecData(): SchemaTypeSpecData? {
    return when (this) {
        is Component.Schema.EnumString -> SchemaTypeSpecData.Enum(this)
        is Component.Schema.Composed -> toObjOrNull()?.toTypeSpecData()
            ?: SchemaTypeSpecData.ValueClass(
                Component.Schema.Unknown.Value(
                    name = name,
                    typeWithFormat = typeWithFormat
                )
            )

        is Component.Schema.SealedInterface -> SchemaTypeSpecData.SealedInterface(this)
        is Component.Schema.Obj -> SchemaTypeSpecData.DataClass(this)
        is Component.Schema.Value -> SchemaTypeSpecData.ValueClass(this)
    }
}

context(_: CodeGenContext)
internal fun Component.Schema.NonInline.toTypeSpec(): TypeSpec? {
    getMappingOrNull()?.also { return null }
    return toTypeSpecData()?.toTypeSpec()
}

context(_: CodeGenContext)
internal fun Component.Schema.NonInline.toInnerTypeSpec(): TypeSpec? { // TODO why?
    getMappingOrNull()?.also { return null }
    return when (this) {
        is Component.Schema.EnumString -> generateEnumTypeSpec()
        is Component.Schema.Composed -> toObjOrNull()?.toInnerTypeSpec()
        is Component.Schema.SealedInterface -> generateSealedInterfaceTypeSpec()
        is Component.Schema.Obj -> generateDataClassTypeSpec()
        is Component.Schema.Array.Value,
        is Component.Schema.MapS.Value,
        is Component.Schema.Primitiv.Value,
        is Component.Schema.Unknown.Value -> null
    }
}

context(c: CodeGenContext)
private fun Component.Schema.Value.generateValueClassTypeSpec(): TypeSpec = buildValueClass(name.prettyClassName) {
    val type = toInline().getTypeName(withFlow = false)
    val valueName = "value"
    val discInfo = c.discriminatorInfoMap[this@generateValueClassTypeSpec.name]
    if (discInfo != null) {
        addAnnotation(Poet.serialName(discInfo.elementName))
        addAnnotation(Poet.experimentalSerializationApi)
        discInfo.interfaces.forEach { addSuperinterface(it.name.typename) }
    }
    val valueSerializer = type.getSerializerTypeName(register = true)?.let { innerSerializer ->
        val serializerName = "Serializer"
        val serializerTypeSpec = buildObject(serializerName) {
            addSuperinterface(
                Poet.kSerializer.parameterizedBy(this@generateValueClassTypeSpec.name.typename),
                delegate = CodeBlock.of(
                    "%T(inner = %T, unwrap = %T::$valueName, wrap = ::%T)",
                    Poet.Lib.Core.valueSerializer,
                    innerSerializer,
                    this@generateValueClassTypeSpec.name.typename,
                    this@generateValueClassTypeSpec.name.typename,
                )
            )
        }
        addType(serializerTypeSpec)
        this@generateValueClassTypeSpec.name.typename.nestedClass(serializerName)
    }
    addAnnotation(Poet.serializable(valueSerializer))
    primaryConstructor {
        addParameter(valueName, type.poet)
        addProperty(valueName, type.poet) {
            initializer(valueName)
        }
    }
    addFunction("toString") {
        addModifiers(KModifier.OVERRIDE)
        returns(String::class)
        if (type == Type.PrimitiveType.String)
            addStatement("return %N", valueName)
        else
            addStatement("return %N.toString()", valueName)
    }

    this@generateValueClassTypeSpec.toInnerTypeSpec()?.also {
        addType(it)
    }
    c.childSchemas[this@generateValueClassTypeSpec.name]?.forEach { childSchema -> // TODO recursive
        childSchema.toInnerTypeSpec()?.also {
            addType(it)
        }
    }
}


context(c: CodeGenContext)
private fun Component.Schema.SealedInterface.generateSealedInterfaceTypeSpec(): TypeSpec =
    TypeSpec.interfaceBuilder(name.prettyClassName).apply {
        addModifiers(KModifier.SEALED)
        addAnnotation(Poet.serializable())
        addAnnotation(Poet.experimentalSerializationApi)
        addAnnotation(Poet.jsonClassDiscriminator(this@generateSealedInterfaceTypeSpec.discriminatorName))
        c.childSchemas[name]?.forEach { childSchema -> // TODO recursive
            childSchema.toInnerTypeSpec()?.also {
                addType(it)
            }
        }
    }.build()

context(c: CodeGenContext)
private fun Component.Schema.EnumString.generateEnumTypeSpec(
): TypeSpec = TypeSpec.enumBuilder(name.prettyClassName).apply {
    addAnnotation(Poet.serializable())
    val nameMapping = values.associateWith { it.toSnakeCase(uppercase = true) }
    val valueName = "value"

    nameMapping.forEach { (value, prettyName) ->
        primaryConstructor {
            addParameter(valueName, String::class)
            addProperty(valueName, String::class.asClassName()) { initializer(valueName) }
        }
        addEnumConstant(prettyName) {
            addSuperclassConstructorParameter("%S", value)
            if (prettyName != value)
                addAnnotation(Poet.serialName(value))
        }
    }

    addFunction("toString") {
        addModifiers(KModifier.OVERRIDE)
        returns(String::class)
        addStatement("return %N", valueName)
    }

    addCompanionObject {
        addFunction("fromSerialOrNull") {
            addParameter("value", String::class)
            returns(name.typename.copy(nullable = true))
            addCode {
                beginControlFlow("return when(value)")
                nameMapping.values.forEach { prettyName ->
                    addStatement("%T.%L.$valueName -> %T.%L", name.typename, prettyName, name.typename, prettyName)
                }
                addStatement("else -> null")
                endControlFlow()
            }
        }
        addFunction("fromSerial") {
            addParameter("value", String::class)
            returns(name.typename)
            addCode(
                $$"""return fromSerialOrNull(value) ?: throw %T("Unknown enum value '$value' for enum $${name.prettyClassName}")""",
                ClassName("kotlin", "IllegalArgumentException"),
            )
        }
    }
    c.childSchemas[name]?.forEach { childSchema -> // TODO recursive
        childSchema.toInnerTypeSpec()?.also {
            addType(it)
        }
    }
}.build()

context(c: CodeGenContext)
private fun Component.Schema.Obj.generateDataClassTypeSpec(
): TypeSpec = TypeSpec.classBuilder(name.prettyClassName).apply {
    val type = this@generateDataClassTypeSpec.getTypeName(withFlow = false)
    addModifiers(KModifier.DATA)
    addAnnotation(Poet.serializable(type.getSerializerTypeName(register = true)))
    val discInfo = c.discriminatorInfoMap[this@generateDataClassTypeSpec.name]
    if (discInfo != null) {
        addAnnotation(Poet.serialName(discInfo.elementName))
        addAnnotation(Poet.experimentalSerializationApi)
        discInfo.interfaces.forEach { addSuperinterface(it.name.typename) }
    }
    primaryConstructor {
        this@generateDataClassTypeSpec.properties.forEach { (fieldName, prop) ->
            if (fieldName == discInfo?.discriminatorName) return@forEach
            val prettyFieldName = fieldName.toCamelCase(capitalized = false)
            val isRequired = this@generateDataClassTypeSpec.required.contains(fieldName)
            val propType = prop.getTypeName(withFlow = false)
                .nullable(prop.isNullable)
                .let {
                    if (isRequired)
                        it
                    else
                        Type.NonPrimitiveType.Option(it).nullable(false)
                }
            addParameter(prettyFieldName, propType.poet) {
                if (!isRequired)
                    defaultValue("%T", Poet.option.nestedClass("Undefined"))
            }
            addProperty(prettyFieldName, propType.poet) {
                initializer(prettyFieldName)
                val propSerializer = propType.type.getSerializerTypeName(register = true)
                if (propSerializer != null)
                    addAnnotation(Poet.serializable(propSerializer))
                if (prettyFieldName != fieldName)
                    addAnnotation(Poet.serialName(fieldName))
            }
        }
    }
    c.childSchemas[name]?.forEach { childSchema -> // TODO recursive
        childSchema.toInnerTypeSpec()?.also {
            addType(it)
        }
    }
}.build()
