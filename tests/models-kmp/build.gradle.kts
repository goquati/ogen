import de.quati.ogen.plugin.Severity
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

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
    // One runtime per target is enough: the generated code is the same for all of them, and every extra
    // runtime adds its own test run to the CI.
    jvm()
    js {
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }
    iosArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.goquati.base)
            implementation(libs.kotlinx.coroutine.core)
            implementation(libs.bundles.kotlinx.serialization)
            implementation("de.quati.ogen:core:1.0.0-SNAPSHOT")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest)
        }
        jvmMain.dependencies {
            // the Bean Validation annotations of the models are the Jakarta ones on the JVM
            implementation("jakarta.validation:jakarta.validation-api:3.1.1")
        }
        jvmTest.dependencies {
            implementation("org.hibernate.validator:hibernate-validator:9.1.5.Final")
            implementation("org.glassfish.expressly:expressly:6.0.0")
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
    utilPackageName("$group.oas.schemas.gen.util")
    add(packageName = "$group.oas.schemas.gen") {
        specFile("$projectDir/oas.yaml")
        // Only models, so no operation uses the schemas.
        validator { rules { unusedSchemas = Severity.Off } }
        beanValidation = true
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
