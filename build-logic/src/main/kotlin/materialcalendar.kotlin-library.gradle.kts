plugins {
    id("org.jetbrains.kotlin.jvm")
    id("materialcalendar.lint")
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    "testImplementation"(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
