package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import de.quati.kotlin.util.associateNotNull
import de.quati.kotlin.util.groupByNotNull
import de.quati.kotlin.util.poet.PackageName
import de.quati.kotlin.util.poet.dsl.buildObject
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ElementDiscriminatorInfo
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.model.flatten


internal class CodeGenContext(
    val specConfig: SpecConfig,
    val spec: Spec,
) : ComponentsContext by spec.components {
    val packageModel: PackageName = specConfig.modelConfig.packageName
    val packageShared: PackageName = specConfig.sharedConfig.packageName

    val typeMappings = specConfig.modelConfig.typeMappings
    val schemaMappings = specConfig.modelConfig.schemaMappings
    val schemaPostfix = specConfig.modelConfig.postfix

    val discriminatorInfoMap: Map<ComponentName.Schema, ElementDiscriminatorInfo>
    val refToSchema: Map<RefString, Component.Schema>
    val enumSchemas: Set<Component.Schema.EnumString>

    init {
        val invalidSealedInterfaces = mutableSetOf<Component.Schema.SealedInterface>()
        val allSchemas = spec.components.schemas.flatMap { it.value.flatten() }
        enumSchemas = allSchemas.filterIsInstance<Component.Schema.EnumString>().toSet()

        refToSchema = allSchemas
            .groupByNotNull { it.name.toRefOrNull() }
            .mapValues { (name, schemas) -> schemas.singleOrNull() ?: error("Duplicate schema name '$name'") }

        discriminatorInfoMap = allSchemas
            .filterIsInstance<Component.Schema.SealedInterface>()
            .flatMap { si -> si.schemas.map { it.value to si } }
            .groupBy({ it.first }, { it.second })
            .entries.associateNotNull { (refSchema, interfaces) ->
                val discriminator = interfaces.map { it.discriminatorName }.distinct().singleOrNull()
                val name = interfaces.mapNotNull { it.findChildSchema(refSchema.name) }.distinct().singleOrNull()
                if (name == null || discriminator == null) {
                    invalidSealedInterfaces.addAll(interfaces)
                    return@associateNotNull null
                }
                refSchema.ref.schemaName to ElementDiscriminatorInfo(
                    discriminatorName = discriminator,
                    elementName = name,
                    interfaces = interfaces,
                )
            }
            .filterValues { it.interfaces.intersect(invalidSealedInterfaces).isEmpty() }
    }

    private val serializers = mutableMapOf<Type, String>()
    private val serializerTypeSpecBuilder = TypeSpec.objectBuilder("Serializer")
        .addModifiers(KModifier.INTERNAL)
    val optionSerializer = packageShared.className("OptionSerializer")
    val valueSerializer = packageShared.className("ValueSerializer")
    val operationContext = packageShared.className("OperationContext")

    fun buildSerializerTypeSpec() = serializerTypeSpecBuilder.build()

    fun registerSerializer(
        register: Boolean,
        type: Type,
        delegate: CodeBlock,
    ): TypeName {
        fun serializerClassName(name: String) =
            packageModel.className("Serializer", name)
        serializers[type]?.let {
            return serializerClassName(it)
        }

        val name = type.prettyName()
        val typeName = serializerClassName(name)
        if (!register) return typeName
        serializerTypeSpecBuilder.addType(buildObject(name) {
            addSuperinterface(
                Poet.kSerializer.parameterizedBy(type.poet),
                delegate = delegate
            )
        })
        serializers[type] = name
        return typeName
    }
}