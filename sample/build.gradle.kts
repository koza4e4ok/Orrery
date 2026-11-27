plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
    id("orrery.lint")
}

android {
    namespace = "me.kozakov.orrery.sample"
    compileSdk {
        version =
            release(
                libs.versions.compile.sdk
                    .asProvider()
                    .get()
                    .toInt(),
            ) {
                minorApiLevel =
                    libs.versions.compile.sdk.minor
                        .get()
                        .toInt()
            }
    }

    defaultConfig {
        applicationId = "me.kozakov.orrery.sample"
        minSdk = 23
        targetSdk =
            libs.versions.compile.sdk
                .asProvider()
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":orrery-compose"))
    implementation(project(":orrery-lunar"))
    implementation(project(":orrery-calendars"))
    implementation(project(":orrery-holidays"))
    implementation(project(":orrery-widget"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.androidx.profileinstaller)
    // The home-screen widget glue. Glance stays in :sample, which is never published.
    implementation(libs.glance.appwidget)
}
