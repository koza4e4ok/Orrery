import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// Compose Multiplatform library convention (#20, phase 3). AGP 9 no longer lets the
// Kotlin Multiplatform plugin share a module with com.android.library, so the Android
// target comes from the Android-KMP library plugin (single variant, host tests in
// src/androidHostTest). Source sets added to the default hierarchy template:
//
//   commonMain
//   ├── jvmSharedMain   (android, desktop): java.time / java.util.Locale code
//   └── nonAndroidMain  (desktop): built-in strings without Android resources
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
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

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withCompilations { it.target.name == "android" }
            }
            group("nonAndroid") {
                withJvm()
            }
        }
    }

    compilerOptions {
        // expect/actual objects (PlatformDateFormat) are still Beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Invoking the block enables ABI validation for every target.
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
