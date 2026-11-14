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

// TEMPORARY (#89 task 2 evidence; removed again before review). ci.yml is not changed in
// this PR, so the widget tests and publication ride on checkKotlinAbi, which CI runs for
// every project, and the published POMs and Gradle module metadata are printed to the log.
tasks.named("checkKotlinAbi") {
    finalizedBy("desktopTest", "testAndroidHostTest", "wasmJsTest", "publishToMavenLocal", "printPublishedPin")
}
tasks.register("printPublishedPin") {
    mustRunAfter("publishToMavenLocal")
    val repo = File(System.getProperty("user.home"), ".m2/repository/me/kozakov/orrery")
    val pin = "[${providers.gradleProperty("VERSION_NAME").get()}]"
    doLast {
        val files =
            repo
                .walkTopDown()
                .filter { it.isFile && it.name.startsWith("orrery-widget") }
                .filter { it.extension == "pom" || it.extension == "module" }
                .sortedBy { it.path }
                .toList()
        check(files.isNotEmpty()) { "no orrery-widget publication in $repo" }
        files.forEach { file ->
            println("PIN-89 ===== ${file.relativeTo(repo)}")
            file.readLines().forEach { println("PIN-89 | $it") }
            val text = file.readText()
            check(!text.contains("glance", ignoreCase = true)) { "${file.name} mentions Glance" }
            if (file.extension == "pom") {
                val unpinned = Regex("<groupId>me.kozakov.orrery</groupId>\\s*<artifactId>[^<]+</artifactId>\\s*<version>[^\\[]")
                check(text.contains("<version>$pin</version>")) { "${file.name} has no $pin pin" }
                check(!unpinned.containsMatchIn(text)) { "${file.name} has an unpinned orrery-* dependency" }
            }
        }
        println("PIN-89 checked ${files.size} files")
    }
}
