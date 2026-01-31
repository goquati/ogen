pluginManagement {
    includeBuild("../plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "tests"
include("ktor-client")
include("models-kmp")
include("models-v30")
include("models-v31")
include("springbootv4")
