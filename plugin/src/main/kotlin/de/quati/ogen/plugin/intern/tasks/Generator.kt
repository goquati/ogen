package de.quati.ogen.plugin.intern.tasks

import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.constant.operationContextTypeSpec
import de.quati.ogen.plugin.intern.codegen.constant.optionSerializerTypeSpec
import de.quati.ogen.plugin.intern.codegen.endpoint.syncEndpoints
import de.quati.ogen.plugin.intern.codegen.toTypeSpec
import de.quati.ogen.plugin.intern.codegen.constant.valueSerializerTypeSpec
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import de.quati.ogen.plugin.intern.model.config.ModelConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.model.config.SharedConfig
import org.gradle.api.file.Directory
import org.gradle.internal.logging.text.StyledTextOutput

internal class Generator(
    private val out: StyledTextOutput,
    private val rootOutputDir: Directory
) {
    fun generate(
        configs: SpecConfigs
    ) {
        configs.specs.forEach { config ->
            Validator.validate(config = config, out = out)
            with(config.toCodeGenContext()) {
                generateModels(config = config.modelConfig)
                generateUtils(config = config.sharedConfig)
                config.generatorConfigs.forEach { generatorConfig ->
                    generate(config = generatorConfig)
                }
            }
        }
    }

    context(_: CodeGenContext)
    private fun generate(config: GeneratorConfig) {
        directorySync(packageName = config.packageName) {
            config.syncEndpoints()
        }
    }

    context(c: CodeGenContext)
    private fun generateModels(config: ModelConfig) {
        if (!config.generate) return
        directorySync(packageName = config.packageName) {
            c.spec.components.schemas.forEach { (_, schema) ->
                val typeSpec = schema.toTypeSpec() ?: return@forEach
                sync(fileName = "${schema.name.fileName}.kt") {
                    addType(typeSpec)
                }
            }

            sync(fileName = "_utils.kt") {
                addType(c.buildSerializerTypeSpec())
            }
        }
    }

    private fun generateUtils(config: SharedConfig) {
        if (!config.generate) return
        directorySync(packageName = config.packageName) {
            sync(fileName = "ValueSerializer.kt") {
                addType(valueSerializerTypeSpec)
            }
            sync(fileName = "OptionSerializer.kt") {
                addType(optionSerializerTypeSpec)
            }
            sync(fileName = "OperationContext.kt") {
                addType(operationContextTypeSpec(packageName = config.packageName))
            }
        }
    }

    private fun directorySync(
        packageName: PackageName,
        block: DirectorySyncService.() -> Unit
    ) = DirectorySyncService(
        rootDir = rootOutputDir,
        packageName = packageName,
        out = out,
    ).use(block)
}