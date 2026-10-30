plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
    id("orrery.lint")
}

android {
    namespace = "me.kozakov.orrery.sample"
    compileSdk =
        libs.versions.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "me.kozakov.orrery.sample"
        minSdk = 23
        targetSdk =
            libs.versions.compile.sdk
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
    // TEMPORARY (README GIF): Robolectric frame capture in SampleTourRecorder.
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all {
            it.systemProperty("roborazzi.test.record", "true")
            it.maxHeapSize = "4g"
        }
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.androidx.profileinstaller)
    // TEMPORARY (README GIF): Robolectric frame capture in SampleTourRecorder.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
