plugins {
    id("orrery.kmp-library")
    id("orrery.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":orrery-core"))
            api(libs.kotlinx.datetime)
        }
    }
}
