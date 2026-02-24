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

    implementation(libs.bundles.ktor.client)

    implementation(libs.goquati.base)
    implementation(libs.bundles.kotlinx.coroutine)
    implementation(libs.bundles.kotlinx.serialization)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
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
    add(packageName = "$group.oas.schemas.gen") {
        specDirectory(path = "$projectDir/oas")
        model {}
        serverSpringV4 {}
    }
}

tasks.test {
    useJUnitPlatform()
}
