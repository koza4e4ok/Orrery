// Interactive web demo deployed next to the API reference at
// https://koza4e4ok.github.io/Orrery/demo/ by .github/workflows/docs.yml. Not published.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("orrery.lint")
}

kotlin {
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
        wasmJsMain.dependencies {
            implementation(project(":orrery-compose"))
            implementation(project(":orrery-holidays"))
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material3)
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
