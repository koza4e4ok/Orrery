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
        jvmTest.dependencies {
            // Test oracle only (Unicode License v3); never part of the published POM.
            implementation(libs.icu4j)
        }
    }
}
