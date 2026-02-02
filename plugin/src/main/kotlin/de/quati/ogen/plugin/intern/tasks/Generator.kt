package de.quati.ogen.plugin.intern.tasks

import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.generator.sync
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
        configs.specs.forEach { config ->
            Validator.validate(config = config, out = out)
            with(config.toCodeGenContext()) {
                config.generatorConfigs.forEach { generatorConfig ->
                    generate(config = generatorConfig)
                }
            }
        }
    }

    context(_: CodeGenContext)
    private fun generate(config: GeneratorConfig) {
        if (config.skipGeneration) return
        directorySync(packageName = config.packageName) {
            when (config) {
                is GeneratorConfig.Shared -> config.sync()
                is GeneratorConfig.Model -> config.sync()
                is GeneratorConfig.ServerSpringV4 -> config.sync()
                is GeneratorConfig.ClientKtor.Util -> config.sync()
                is GeneratorConfig.ClientKtor -> {
                    config.sync()
                    generate(config.util)
                }
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