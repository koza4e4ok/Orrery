import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// Compose Multiplatform library convention (#20, phases 3-4). AGP 9 no longer lets the
// Kotlin Multiplatform plugin share a module with com.android.library, so the Android
// target comes from the Android-KMP library plugin (single variant, host tests in
// src/androidHostTest). iOS klibs compile on Linux; iOS tests need a macOS host.
// Source sets added to the default hierarchy template:
//
//   commonMain
//   ├── jvmSharedMain       (android, desktop): java.time / java.util.Locale code
//   └── nonAndroidMain      (desktop, ios*, wasmJs): built-in strings without Android resources
//       └── nonJvmMain      (ios*, wasmJs): pure-Kotlin string templates
//
// iosMain (NSDateFormatter) and wasmJsMain (Intl.DateTimeFormat) come from the template.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    // Supplies the Skiko Wasm runtime (skiko.mjs) that wasmJs test bundles import.
    id("org.jetbrains.compose")
    id("orrery.lint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    explicitApi()
    jvmToolchain(17)

    android {
        compileSdk =
            libs
                .findVersion("compile-sdk")
                .get()
                .requiredVersion
                .toInt()
        minSdk = 23
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    jvm("desktop")
    // No iosX64: Compose Multiplatform 1.11 no longer publishes Intel simulator klibs.
    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.target.name == "android" }
            }
            group("nonAndroid") {
                withJvm()
                group("nonJvm") {
                    withIos()
                    withWasmJs()
                }
            }
        }
    }

    compilerOptions {
        // expect/actual objects (PlatformDateFormat) are still Beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Invoking the block enables ABI validation: JVM dumps for android/desktop plus a
    // merged klib dump for the iOS and wasmJs targets.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
    }
}

// The lint plugin's plain `detekt` task only scans src/main and src/test by default.
detekt {
    source.setFrom(
        fileTree("src") {
            include("*/kotlin/**/*.kt")
        },
    )
}
