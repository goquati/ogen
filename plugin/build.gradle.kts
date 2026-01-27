import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.kotlinJvm)
}

val groupStr = "de.quati.ogen"
val gitRepo = "https://github.com/goquati/ogen"

version = System.getenv("GIT_TAG_VERSION") ?: "1.0.0-SNAPSHOT"
group = groupStr

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.poet)
    implementation(libs.goquati.base)
    implementation(libs.goquati.poet)
    implementation(libs.swagger.parser)
    implementation(libs.swagger.generator)
    compileOnly(kotlin("gradle-plugin"))
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget.set(JvmTarget.JVM_21)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

gradlePlugin {
    website = gitRepo
    vcsUrl = "$gitRepo.git"

    val ogen by plugins.creating {
        id = groupStr
        implementationClass = "$groupStr.plugin.OgenPlugin"
        displayName = "ogen: OpenAPI generator for Kotlin"
        description = "Idiomatic OpenAPI generator for Kotlin and KMP, supporting kotlinx.serialization, OpenAPI 3.0/3.1, and Spring Boot."
        tags = listOf("openapi", "kotlin", "kmp", "multiplatform", "kotlinx-serialization", "generator", "spring-boot", "ktor")
    }
}