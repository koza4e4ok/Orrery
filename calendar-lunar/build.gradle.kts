plugins {
    id("materialcalendar.kotlin-library")
    id("materialcalendar.publish")
}

dependencies {
    api(project(":calendar-core"))
    api(libs.kotlinx.datetime)
}
