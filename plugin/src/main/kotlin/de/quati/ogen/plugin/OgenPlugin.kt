package de.quati.ogen.plugin

import de.quati.ogen.plugin.intern.tasks.Generator
import de.quati.ogen.plugin.intern.tasks.Validator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension


public class OgenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val configBuilder = project.extensions.create("ogen", SpecsConfigBuilder::class.java)
        val mainName = when (project.kotlinExtension) {
            is KotlinMultiplatformExtension -> "commonMain"
            is KotlinProjectExtension -> "main"
        }
        val genDir = project.layout.buildDirectory.dir("generated/sources/ogen/src/$mainName/kotlin")

        val ogenGenerate = project.tasks.register("ogenGenerate") { task ->
            task.notCompatibleWithConfigurationCache(CONFIGURATION_CACHE_INCOMPATIBILITY_REASON)
            task.outputs.dir(genDir)
            task.outputs.upToDateWhen { false }
            task.group = TASK_GROUP
            task.doLast {
                val configs = configBuilder.build()
                Generator(
                    rootOutputDir = genDir.get().asFile.toPath(),
                    logger = it.logger,
                ).generate(configs = configs)
            }
        }

        project.tasks.register("ogenValidate") { task ->
            task.notCompatibleWithConfigurationCache(CONFIGURATION_CACHE_INCOMPATIBILITY_REASON)
            task.group = TASK_GROUP
            task.doLast {
                val configs = configBuilder.build()
                Validator(it.logger).validate(configs)
            }
        }

        project.afterEvaluate {
            project.kotlinExtension.sourceSets.findByName(mainName)?.kotlin?.srcDir(ogenGenerate)
        }
    }

    private companion object {
        private const val TASK_GROUP = "quati tools"
        private const val CONFIGURATION_CACHE_INCOMPATIBILITY_REASON =
            "ogen tasks capture Swagger model types incompatible with configuration cache serialization"
    }
}