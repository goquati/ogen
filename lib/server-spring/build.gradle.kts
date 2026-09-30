import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets {
        jvmMain.dependencies {
            compileOnly(libs.spring.webflux)
            compileOnly(libs.spring.boot.autoconfigure)
        }
    }
}
