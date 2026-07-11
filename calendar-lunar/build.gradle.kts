plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":calendar-core"))
    api(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
