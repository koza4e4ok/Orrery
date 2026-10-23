import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// Kotlin Multiplatform library convention (#20). Targets follow the Compose
// Multiplatform matrix minus Android: a plain jvm() target serves both Android
// and Desktop consumers. iOS klibs compile on Linux; iOS tests need a macOS host.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("orrery.lint")
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // Invoking the block enables ABI validation: a JVM dump plus a merged klib dump
    // for the native and wasm targets.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
