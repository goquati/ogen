pluginManagement {
    // Maven Central as a fallback: the Plugin Portal only proxies most artifacts from there, and an outage of
    // that proxy failed CI builds.
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "plugin"