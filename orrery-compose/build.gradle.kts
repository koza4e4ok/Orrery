plugins {
    id("orrery.android-library")
    id("orrery.publish")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "me.kozakov.orrery.compose"
}

roborazzi {
    outputDir.set(file("src/test/snapshots"))
}

dependencies {
    api(project(":orrery-core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
