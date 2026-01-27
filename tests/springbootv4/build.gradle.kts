import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.spring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
    id("de.quati.ogen")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation(libs.goquati.base)
    implementation(libs.bundles.kotlinx.coroutine)
    implementation(libs.bundles.kotlinx.serialization)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
        packageName = "$group.gen",
    ) {
        validator {
            failOnWarnings = true
        }
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
        serverSpringV4 {
            addOperationContext = true
            contextIfAnySecurity("$group.AuthContext")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
