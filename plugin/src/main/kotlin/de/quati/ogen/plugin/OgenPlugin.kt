package de.quati.ogen.plugin

import de.quati.ogen.plugin.intern.tasks.Generator
import de.quati.ogen.plugin.intern.tasks.Validator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.internal.logging.text.StyledTextOutputFactory
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
            task.outputs.dir(genDir)
            task.outputs.upToDateWhen { false }
            task.group = TASK_GROUP
            task.doLast {
                val out = project.serviceOf<StyledTextOutputFactory>().create("ogen")!!
                val configs = configBuilder.build()
                Generator(
                    rootOutputDir = genDir.get(),
                    out = out,
                ).generate(configs = configs)
            }
        }

        project.tasks.register("ogenValidate") { task ->
            task.group = TASK_GROUP
            task.doLast {
                val out = project.serviceOf<StyledTextOutputFactory>().create("ogen")!!
                val configs = configBuilder.build()
                Validator(out).validate(configs)
            }
        }

        project.afterEvaluate {
            project.kotlinExtension.sourceSets.findByName(mainName)?.kotlin?.srcDir(ogenGenerate)
        }
    }

    private companion object {
        private const val TASK_GROUP = "quati tools"
    }
}