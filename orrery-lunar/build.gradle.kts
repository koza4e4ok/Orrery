plugins {
    id("orrery.kotlin-library")
    id("orrery.publish")
}

dependencies {
    api(project(":orrery-core"))
    api(libs.kotlinx.datetime)
}
