// TEMPORARY: renders the README demo GIFs (iOS, Web, Desktop) in .github/workflows/demo-gifs.yml.
// Included only with -Porrery.demo=true; removed again before the PR is finalized.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("orrery.cmp-library")
}

kotlin {
    android {
        namespace = "me.kozakov.orrery.demo"
    }
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
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material3)
        }
        getByName("desktopMain").dependencies {
            implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:" + libs.versions.compose.multiplatform.asProvider().get())
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
