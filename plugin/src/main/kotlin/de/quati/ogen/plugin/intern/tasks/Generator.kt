package de.quati.ogen.plugin.intern.tasks

import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.GlobalGenContext
import de.quati.ogen.plugin.intern.codegen.generator.sync
import de.quati.ogen.plugin.intern.codegen.util.syncClientKtorUtils
import de.quati.ogen.plugin.intern.codegen.util.syncModelUtils
import de.quati.ogen.plugin.intern.codegen.util.syncServerSpringV4Utils
import de.quati.ogen.plugin.intern.codegen.util.syncUtils
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import org.gradle.api.file.Directory
import org.gradle.internal.logging.text.StyledTextOutput

internal class Generator(
    private val out: StyledTextOutput,
    private val rootOutputDir: Directory
) {
    fun generate(
        configs: SpecConfigs
    ) {
        with(GlobalGenContext(specConfigs = configs)) {
            configs.specs.forEach { config ->
                Validator.validate(config = config, out = out)
                with(config.toCodeGenContext()) {
                    config.generatorConfigs.forEach { generatorConfig ->
                        generate(config = generatorConfig)
                    }
                }
            }

            syncUtils()
            syncModelUtils()
            syncServerSpringV4Utils()
            syncClientKtorUtils()
        }
    }

    context(_: CodeGenContext)
    private fun generate(config: GeneratorConfig) {
        if (config.skipGeneration) return
        directorySync(packageName = config.packageName) {
            when (config) {
                is GeneratorConfig.Model -> config.sync()
                is GeneratorConfig.ServerSpringV4 -> config.sync()
                is GeneratorConfig.ClientKtor -> config.sync()
            }
        }
    }

    fun directorySync(
        packageName: PackageName,
        block: DirectorySyncService.() -> Unit
    ) = DirectorySyncService(
        rootDir = rootOutputDir,
        packageName = packageName,
        out = out,
    ).use(block)
}