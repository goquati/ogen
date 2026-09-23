pluginManagement {
    includeBuild("../plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "tests"
include("models-kmp")
include("models-v30")
include("models-v31")
include("oas-dir")
include("server-client")
includeBuild("../lib")
