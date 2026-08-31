package de.quati.ogen.plugin.intern.codegen

import de.quati.kotlin.util.associateNotNull
import de.quati.kotlin.util.groupByNotNull
import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ElementDiscriminatorInfo
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.config.SpecConfig


internal class CodeGenContext(
    val specConfig: SpecConfig,
    val spec: Spec,
    val globalGenContext: GlobalGenContext,
) : ComponentsContext by spec.components {
    val utilConfig = globalGenContext.specConfigs.util
    val packageModel: PackageName = specConfig.modelConfig.packageName
    val typeMappings = specConfig.modelConfig.typeMappings
    val schemaMappings = specConfig.modelConfig.schemaMappings
    val schemaPostfix = specConfig.modelConfig.postfix

    val discriminatorInfoMap: Map<ComponentName.Schema, ElementDiscriminatorInfo>
    val allSchemas = spec.components.schemas
    val childSchemas = allSchemas.values.groupByNotNull { it.name.getParent() }
    val enumSchemas = allSchemas.values.filterIsInstance<Component.Schema.EnumString>().toSet()

    init {
        val invalidSealedInterfaces = mutableSetOf<Component.Schema.SealedInterface>()
        discriminatorInfoMap = allSchemas.values
            .filterIsInstance<Component.Schema.SealedInterface>()
            .flatMap { si -> si.schemas.map { it.value to si } }
            .groupBy({ it.first }, { it.second })
            .entries.associateNotNull { (refSchema, interfaces) ->
                val discriminator = interfaces.map { it.discriminatorName }.distinct().singleOrNull()
                val name = interfaces.mapNotNull {
                    it.schemas.entries.firstOrNull { (_, schema) -> schema.ref == refSchema.ref }?.key
                }.distinct().singleOrNull()
                if (name == null || discriminator == null) {
                    invalidSealedInterfaces.addAll(interfaces)
                    return@associateNotNull null
                }
                refSchema.name to ElementDiscriminatorInfo(
                    discriminatorName = discriminator,
                    elementName = name,
                    interfaces = interfaces,
                )
            }
            .filterValues { it.interfaces.intersect(invalidSealedInterfaces).isEmpty() }
    }
}