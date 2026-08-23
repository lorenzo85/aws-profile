import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform") version "2.4.10"
}

group = "com.argol.awsprofile"
version = providers.gradleProperty("version").getOrElse("dev")

val generateVersion by tasks.registering {
    val appVersion = project.version.toString()
    inputs.property("version", appVersion)
    val outputDir = layout.buildDirectory.dir("generated/kotlin")
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile
            .resolve("com/argol/awsprofile/Version.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.argol.awsprofile

            internal const val APP_VERSION = "$appVersion"
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    val nativeTargets = listOf(
        macosArm64(),
        macosX64(),
        linuxX64(),
        linuxArm64()
    )

    nativeTargets.forEach { target ->
        target.binaries {
            executable {
                entryPoint = "com.argol.awsprofile.main"
                baseName = "aws-profile"
            }
        }
        // Opt in to cinterop experimental APIs for all native compilations
        target.compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
                        "-opt-in=kotlin.experimental.ExperimentalNativeApi"
                    )
                }
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        nativeMain {
            kotlin.srcDir(
                tasks.named("generateVersion").map {
                    layout.buildDirectory.dir("generated/kotlin").get()
                }
            )
            dependencies {
                // No external runtime dependencies — this is a self-contained native binary
            }
        }
        nativeTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// Ensure version file is generated before any compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateVersion)
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn(generateVersion)
}
