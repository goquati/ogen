package de.quati.ogen.plugin

import de.quati.ogen.plugin.intern.tasks.Generator
import de.quati.ogen.plugin.intern.tasks.Validator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile


public class OgenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val configBuilder = project.extensions.create("ogen", SpecsConfigBuilder::class.java)
        val multiplatform = when (project.kotlinExtension) {
            is KotlinMultiplatformExtension -> true
            is KotlinProjectExtension -> false
        }
        val mainName = if (multiplatform) "commonMain" else "main"
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
                    multiplatform = multiplatform,
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
            // Hibernate Validator reads the constraints of list items and map values, e.g. List<@Size String>,
            // from JVM type annotations, which the compiler only writes with this flag
            if (configBuilder.beanValidation)
                project.tasks.withType(KotlinJvmCompile::class.java).configureEach { task ->
                    task.compilerOptions.freeCompilerArgs.add(EMIT_JVM_TYPE_ANNOTATIONS)
                }
        }
    }

    private companion object {
        private const val TASK_GROUP = "quati tools"
        private const val EMIT_JVM_TYPE_ANNOTATIONS = "-Xemit-jvm-type-annotations"
        private const val CONFIGURATION_CACHE_INCOMPATIBILITY_REASON =
            "ogen tasks capture Swagger model types incompatible with configuration cache serialization"
    }
}