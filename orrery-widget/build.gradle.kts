plugins {
    id("orrery.cmp-library")
    id("orrery.publish")
    alias(libs.plugins.roborazzi)
}

// Experimental snapshot/widget renderer (#89). Every public declaration is
// @ExperimentalOrreryApi and lives in me.kozakov.orrery.widget, which the ABI dump
// filters exclude, so the committed dumps stay empty until the module graduates.
kotlin {
    android {
        namespace = "me.kozakov.orrery.widget"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":orrery-core"))
            // Shared @InternalOrreryApi month-drawing primitives, PlatformDateFormat and colours.
            implementation(project(":orrery-compose"))
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.material3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // runTest for the suspend renderer on every target, wasmJs included (test only).
            implementation(libs.kotlinx.coroutines.test)
        }
        // Skiko's native library, needed to draw into a bitmap in desktop tests.
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            // Same androidx versions as orrery-compose on Android.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
        // Same Robolectric and Roborazzi setup as orrery-compose.
        getByName("androidHostTest").dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.ext.junit)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
        }
    }
}

roborazzi {
    outputDir.set(file("src/androidHostTest/snapshots"))
}

// CI runs :orrery-widget:testAndroidHostTest, so that run also verifies the goldens: with the
// verify task in the graph, Roborazzi runs the host tests in verify mode and a missing or
// changed golden fails them (the *_actual.png files land in build/outputs/roborazzi).
// Record and compare runs keep their own mode.
val roborazziModeRequested =
    gradle.startParameter.taskNames.any { name ->
        listOf("recordRoborazzi", "compareRoborazzi", "verifyAndRecordRoborazzi").any { it in name }
    }
if (!roborazziModeRequested) {
    tasks.named { it == "testAndroidHostTest" }.configureEach {
        finalizedBy("verifyRoborazziAndroidHostTest")
    }
}

// The Android-KMP plugin has a single variant, so the host-test tasks lost their
// "Debug" suffix. Same aliases as orrery-compose.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Alias for testAndroidHostTest."
    dependsOn("testAndroidHostTest")
}
tasks.register("verifyRoborazziDebug") {
    group = "verification"
    description = "Alias for verifyRoborazziAndroidHostTest."
    dependsOn("verifyRoborazziAndroidHostTest")
}
tasks.register("recordRoborazziDebug") {
    group = "verification"
    description = "Alias for recordRoborazziAndroidHostTest."
    dependsOn("recordRoborazziAndroidHostTest")
}

// orrery-widget calls @InternalOrreryApi declarations of orrery-compose, which have no
// compatibility promise, so it only works with the exact same orrery-compose version.
// The constraint writes `strictly` into the Gradle module metadata.
dependencies {
    constraints {
        add("commonMainImplementation", "me.kozakov.orrery:orrery-compose") {
            version {
                strictly(providers.gradleProperty("VERSION_NAME").get())
            }
            because("orrery-widget uses internal orrery-compose API; all orrery-* artefacts must share one version")
        }
    }
}

// Maven ignores Gradle's `strictly`, so the POM gets exact `[x.y.z]` ranges for every
// orrery-* dependency. The pin is a local value inside configureEach so the withXml
// action captures only a String (a script-level val breaks the configuration cache).
publishing {
    publications.withType<MavenPublication>().configureEach {
        val pin = "[${providers.gradleProperty("VERSION_NAME").get()}]"
        pom.withXml {
            val dependencies = asElement().getElementsByTagName("dependency")
            for (i in 0 until dependencies.length) {
                val dependency = dependencies.item(i) as org.w3c.dom.Element
                val group = dependency.getElementsByTagName("groupId").item(0)?.textContent
                if (group == "me.kozakov.orrery") {
                    dependency.getElementsByTagName("version").item(0)?.textContent = pin
                }
            }
        }
    }
}
