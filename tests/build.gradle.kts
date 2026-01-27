plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.spring) apply false
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDependencyManagement) apply false
}

subprojects {
    repositories {
        mavenCentral()
    }
    group = "de.quati.ogen"
    version = "1.0-SNAPSHOT"
}