// TEMPORARY (README GIFs, .github/workflows/demo-gifs.yml): adds Desktop and iOS targets.
// The workflow moves the demo UI to commonMain first. Removed before the PR is finalized.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("orrery.lint")
}

kotlin {
    jvm("desktop")
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "OrreryDemo"
            isStatic = false
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "demo.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":orrery-compose"))
            implementation(project(":orrery-holidays"))
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material3)
        }
        getByName("desktopMain").dependencies {
            implementation(
                "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:" +
                    libs.versions.compose.multiplatform
                        .asProvider()
                        .get(),
            )
        }
    }
}

tasks.register<JavaExec>("runDesktopDemo") {
    val compilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    dependsOn(compilation.compileTaskProvider)
    classpath = files(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("me.kozakov.orrery.demo.DesktopMainKt")
    jvmArgs("-Dsun.java2d.uiScale=2")
}

// The lint plugin's plain `detekt` task only scans src/main and src/test by default.
detekt {
    source.setFrom(
        fileTree("src") {
            include("*/kotlin/**/*.kt")
        },
    )
}
