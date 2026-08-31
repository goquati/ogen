import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.publish)
}

repositories {
    mavenCentral()
}

val githubUser = "goquati"
val githubProject = "ogen"

enum class SubProjects(val projectName: String) {
    CORE("core"),
    CLIENT_KTOR("client-ktor"),
}

tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false // disable for the root project
}

subprojects {
    val projectType = SubProjects.values().singleOrNull { it.projectName == name }
        ?: throw NotImplementedError("no description defined for $name")

    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "com.vanniktech.maven.publish")

    repositories {
        mavenCentral()
    }
    group = "de.quati.ogen"
    version = System.getenv("GIT_TAG_VERSION") ?: "1.0-SNAPSHOT"

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin")
                useVersion(libs.versions.kotlin.get())
        }
    }

    extensions.configure<KotlinMultiplatformExtension> {
        jvmToolchain(21)
        explicitApi()

        jvm {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
        js {
            browser()
            nodejs()
        }
        iosX64()
        iosArm64()
        macosArm64()

        compilerOptions {
            allWarningsAsErrors = true
            apiVersion.set(KotlinVersion.KOTLIN_2_2)
            languageVersion.set(KotlinVersion.KOTLIN_2_2)
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    val artifactId = project.name

    mavenPublishing {
        val descriptionStr = when (projectType) {
            SubProjects.CORE -> "Shared runtime types for ogen-generated Kotlin code: Option/value/discriminator serializers, security requirement models, and operation context."
            SubProjects.CLIENT_KTOR -> "Ktor HTTP client runtime support for ogen-generated API clients."
        }
        coordinates(
            groupId = project.group as String,
            artifactId = artifactId,
            version = project.version as String
        )
        pom {
            name = artifactId
            description = descriptionStr
            url = "https://github.com/$githubUser/$githubProject"
            licenses {
                license {
                    name = "MIT License"
                    url = "https://github.com/$githubUser/$githubProject/blob/main/LICENSE"
                }
            }
            developers {
                developer {
                    id = githubUser
                    name = githubUser
                    url = "https://github.com/$githubUser"
                }
            }
            scm {
                url = "https://github.com/${githubUser}/${githubProject}"
                connection = "scm:git:https://github.com/${githubUser}/${githubProject}.git"
                developerConnection = "scm:git:git@github.com:${githubUser}/${githubProject}.git"
            }
        }
        publishToMavenCentral(
            SonatypeHost.CENTRAL_PORTAL,
            automaticRelease = true,
        )
        signAllPublications()
    }
}
