plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    id("materialcalendar.lint")
}

android {
    namespace = "dev.koza4e4ok.material.calendar.sample"
    compileSdk =
        libs.versions.compile.sdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.koza4e4ok.material.calendar.sample"
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
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":calendar-compose"))
    implementation(project(":calendar-lunar"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
}
