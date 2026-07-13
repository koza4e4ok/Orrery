import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("orrery.lint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    compileSdk =
        libs
            .findVersion("compile-sdk")
            .get()
            .requiredVersion
            .toInt()
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}
