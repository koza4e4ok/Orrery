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
        // Compose Multiplatform 1.12 fails checkComposeUiTestConfigurationForWasmJs unless
        // the wasmJs tests are bundled with webpack, which needs an executable binary to
        // load the Skiko runtime (CMP-4906). The published klib is unaffected.
        binaries.executable()
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
        // Opt-in API carries no compatibility promise, so it stays out of the dumps (#89).
        // The Compose compiler adds unannotated synthetic $stableprop declarations for
        // annotated classes to the klib dump, so whole packages are excluded as well: the
        // @InternalOrreryApi primitives live in compose.internal, and every declaration of
        // the experimental orrery-widget module is in the widget package.
        filters {
            exclude {
                annotatedWith.addAll(
                    "me.kozakov.orrery.ExperimentalOrreryApi",
                    "me.kozakov.orrery.InternalOrreryApi",
                )
                byNames.add("me.kozakov.orrery.compose.internal.**")
                byNames.add("me.kozakov.orrery.widget.**")
            }
        }
    }
}

// The ABI filters above and Dokka hide these packages as a whole, so every public
// declaration in them must carry its opt-in marker (#89). checkKotlinAbi depends on the
// check, so the existing CI step runs it in every Compose module.
val checkOptInMarkers =
    tasks.register<CheckOptInMarkersTask>("checkOptInMarkers") {
        group = "verification"
        description = "Fails when a public declaration in an opt-in package lacks its marker annotation."
        sources.from(
            fileTree("src") {
                include("*Main/kotlin/**/*.kt")
            },
        )
        packages.put("me.kozakov.orrery.widget", "ExperimentalOrreryApi")
        packages.put("me.kozakov.orrery.compose.internal", "InternalOrreryApi")
    }
tasks.named { it == "checkKotlinAbi" || it == "check" }.configureEach {
    dependsOn(checkOptInMarkers)
}

// The lint plugin's plain `detekt` task only scans src/main and src/test by default.
detekt {
    source.setFrom(
        fileTree("src") {
            include("*/kotlin/**/*.kt")
        },
    )
}
