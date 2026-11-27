import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

extensions.configure<KotlinMultiplatformExtension> {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                withJs()
                withWasmJs()
                withNative()
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.quati.base)
            implementation(libs.kotlinx.serialization)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            compileOnly(libs.jakarta.validation.api)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.jakarta.validation.api)
        }
    }
}
