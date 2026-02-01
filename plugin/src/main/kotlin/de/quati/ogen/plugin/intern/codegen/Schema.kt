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
        c.schemaMappings[ref.schemaName]?.also { return it }
        return requireNotNull(c.refToSchema[ref]).getMappingOrNull()
    }
    c.schemaMappings[name]?.also { return it }
    c.typeMappings[typeWithFormat]?.also { return it }
    return null
}

context(c: CodeGenContext)
internal fun Component.Schema.getTypeName(
    withFlow: Boolean,
): Type {
    getMappingOrNull()?.also { return it }
    return when (this) {
        is Component.Schema.Array -> items.getTypeName(withFlow = false).let { itemTypeData ->
            when (withFlow) {
                true -> Type.NonPrimitiveType.Flow(itemTypeData)
                false -> Type.NonPrimitiveType.List(itemTypeData)
            }
        }

        is Component.Schema.Unknown -> Type.PrimitiveType.JsonElement

        is Component.Schema.PrimitivType -> when (type) {
            Component.Schema.PrimitivType.Type.STRING -> when (typeWithFormat.format) {
                "uuid" -> Type.PrimitiveType.Uuid
                else -> Type.PrimitiveType.String
            }

            Component.Schema.PrimitivType.Type.INTEGER -> when (format) {
                "int32" -> Type.PrimitiveType.Int
                "int64" -> Type.PrimitiveType.Long
                else -> Type.PrimitiveType.Int
            }

            Component.Schema.PrimitivType.Type.NUMBER -> when (format) {
                "float" -> Type.PrimitiveType.Float
                "double" -> Type.PrimitiveType.Double
                else -> Type.PrimitiveType.Double
            }

            Component.Schema.PrimitivType.Type.BOOLEAN -> Type.PrimitiveType.Boolean
        }

        is Component.Schema.MapS -> Type.NonPrimitiveType.Map(
            keyType = Type.PrimitiveType.String,
            valueType = valueSchema.getTypeName(withFlow = withFlow),
        )

        Component.Schema.Null -> Type.PrimitiveType.JsonNull

        is Component.Schema.Ref -> Type.NonPrimitiveType.SerializableObject(
            packageName = c.packageModel,
            simpleNames = ref.schemaName.classNameSimpleNames
        )

        is Component.Schema.Composed,
        is Component.Schema.SealedInterface,
        is Component.Schema.Obj,
        is Component.Schema.EnumString -> Type.NonPrimitiveType.SerializableObject(
            packageName = c.packageModel,
            simpleNames = name.classNameSimpleNames
        )
    }
}


context(c: CodeGenContext)
private fun Component.Schema.Composed.toObjOrUnknown(): Component.Schema =
    toObjOrNull() ?: Component.Schema.Unknown(name = name, typeWithFormat = typeWithFormat)

context(c: CodeGenContext)
private fun Component.Schema.Composed.toObjOrNull(): Component.Schema.Obj? {
    val innerSchemas = schemas.map { schema ->
        when (schema) {
            is Component.Schema.Obj -> schema
            is Component.Schema.Composed -> schema.toObjOrNull()
            is Component.Schema.Ref -> when (val refSchema = c.refToSchema[schema.ref]) {
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
    data class ValueClass(override val schema: Component.Schema) : SchemaTypeSpecData
}

context(_: CodeGenContext)
internal fun SchemaTypeSpecData.toTypeSpec(): TypeSpec? = when (this) {
    is SchemaTypeSpecData.Enum -> schema.generateEnumTypeSpec()
    is SchemaTypeSpecData.SealedInterface -> schema.generateSealedInterfaceTypeSpec()
    is SchemaTypeSpecData.DataClass -> schema.generateDataClassTypeSpec()
    is SchemaTypeSpecData.ValueClass -> schema.generateValueClassTypeSpec()
}

context(c: CodeGenContext)
internal fun Component.Schema.toTypeSpecData(): SchemaTypeSpecData? {
    return when (this) {
        is Component.Schema.Ref -> null
        is Component.Schema.EnumString -> SchemaTypeSpecData.Enum(this)
        is Component.Schema.Composed -> toObjOrUnknown().toTypeSpecData()
        is Component.Schema.SealedInterface -> SchemaTypeSpecData.SealedInterface(this)
        is Component.Schema.Obj -> SchemaTypeSpecData.DataClass(this)
        is Component.Schema.Unknown,
        is Component.Schema.Null,
        is Component.Schema.MapS,
        is Component.Schema.Array,
        is Component.Schema.PrimitivType -> SchemaTypeSpecData.ValueClass(this)
    }
}

context(_: CodeGenContext)
internal fun Component.Schema.toTypeSpec(): TypeSpec? {
    getMappingOrNull()?.also { return null }
    return toTypeSpecData()?.toTypeSpec()
}

context(_: CodeGenContext)
internal fun Component.Schema.toInnerTypeSpec(): TypeSpec? {
    getMappingOrNull()?.also { return null }
    return when (this) {
        is Component.Schema.EnumString -> generateEnumTypeSpec()
        is Component.Schema.Composed -> toObjOrUnknown().toInnerTypeSpec()
        is Component.Schema.SealedInterface -> generateSealedInterfaceTypeSpec()
        is Component.Schema.Obj -> generateDataClassTypeSpec()
        is Component.Schema.Array -> items.toInnerTypeSpec()
        is Component.Schema.MapS -> valueSchema.toInnerTypeSpec()
        is Component.Schema.Unknown,
        is Component.Schema.Null,
        is Component.Schema.Ref,
        is Component.Schema.PrimitivType -> null
    }
}

context(c: CodeGenContext)
private fun Component.Schema.generateValueClassTypeSpec(): TypeSpec = buildValueClass(name.prettyClassName) {
    val type = getTypeName(withFlow = false)
    val valueName = "value"
    val discInfo = c.discriminatorInfoMap[name]
    if (discInfo != null) {
        addAnnotation(Poet.serialName(discInfo.elementName))
        addAnnotation(Poet.experimentalSerializationApi)
        discInfo.interfaces.forEach { addSuperinterface(it.name.typename) }
    }
    this@generateValueClassTypeSpec.toInnerTypeSpec()?.also {
        this@buildValueClass.addType(it)
    }
    val valueSerializer = type.getSerializerTypeName(register = true)?.let { innerSerializer ->
        val serializerName = "Serializer"
        val serializerTypeSpec = buildObject(serializerName) {
            addSuperinterface(
                Poet.kSerializer.parameterizedBy(name.typename),
                delegate = CodeBlock.of(
                    "%T(inner = %T, unwrap = %T::$valueName, wrap = ::%T)",
                    c.valueSerializer,
                    innerSerializer,
                    name.typename,
                    name.typename,
                )
            )
        }
        addType(serializerTypeSpec)
        name.typename.nestedClass(serializerName)
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
        addStatement("return %N.toString()", valueName)
    }
}


context(_: CodeGenContext)
private fun Component.Schema.SealedInterface.generateSealedInterfaceTypeSpec(): TypeSpec =
    TypeSpec.interfaceBuilder(name.prettyClassName).apply {
        addModifiers(KModifier.SEALED)
        addAnnotation(Poet.serializable())
        addAnnotation(Poet.experimentalSerializationApi)
        addAnnotation(Poet.jsonClassDiscriminator(this@generateSealedInterfaceTypeSpec.discriminatorName))
    }.build()

context(_: CodeGenContext)
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

            prop.toInnerTypeSpec()?.also {
                this@apply.addType(it)
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
}.build()
