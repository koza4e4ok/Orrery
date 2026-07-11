plugins {
    id("materialcalendar.android-library")
    id("materialcalendar.publish")
}

android {
    namespace = "dev.koza4e4ok.material.calendar.compose"
}

dependencies {
    api(project(":calendar-core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
