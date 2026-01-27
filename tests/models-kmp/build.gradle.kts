import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    id("de.quati.ogen")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin")
            useVersion(kotlin.coreLibrariesVersion)
    }
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    iosX64()
    iosArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.goquati.base)
            implementation(libs.kotlinx.coroutine.core)
            implementation(libs.bundles.kotlinx.serialization)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest)
        }
    }

    compilerOptions {
        allWarningsAsErrors = true
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.add("-Xcontext-parameters")
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

ogen {
    addSpec(
        apiFile = "$projectDir/oas.yaml",
        packageName = "$group.oas.schemas.gen",
    ) {
        model {
            typeMapping(
                type = "string+date-time", clazz = "kotlin.time.Instant",
            )
            schemaMapping(schema = "UserId", clazz = "$group.UserId")
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
