plugins {
    id("orrery.cmp-library")
    id("orrery.publish")
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
    }
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
