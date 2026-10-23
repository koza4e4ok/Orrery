plugins {
    id("orrery.kmp-library")
    id("orrery.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
    }
}
