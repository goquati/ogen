import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    id("de.quati.ogen")
}

dependencies {
    implementation(libs.goquati.base)
    implementation(libs.bundles.kotlinx.coroutine)
    implementation(libs.bundles.kotlinx.serialization)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget.set(JvmTarget.JVM_21)
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
                type = "number+big-decimal", clazz = "java.math.BigDecimal",
                serializerObject = "$group.BigDecimalSerializer",
            )
            typeMapping(
                type = "string+date-time", clazz = "java.time.OffsetDateTime",
                serializerObject = "$group.OffsetDateTimeSerializer",
            )
            schemaMapping(schema = "UserId", clazz = "$group.UserId")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
