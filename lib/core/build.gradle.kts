import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.quati.base)
            implementation(libs.kotlinx.serialization)
        }
    }
}
