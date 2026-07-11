plugins {
    id("materialcalendar.kotlin-library")
}

dependencies {
    api(project(":calendar-core"))
    api(libs.kotlinx.datetime)
}
