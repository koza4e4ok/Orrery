plugins {
    id("org.jetbrains.kotlin.jvm")
    id("orrery.lint")
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    // Invoking the block enables ABI validation (KGP built-in successor to
    // binary-compatibility-validator). JVM modules only; orrery-compose is excluded.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        // Opt-in API carries no compatibility promise, so it stays out of the dumps (#89).
        filters {
            exclude {
                annotatedWith.addAll(
                    "me.kozakov.orrery.ExperimentalOrreryApi",
                    "me.kozakov.orrery.InternalOrreryApi",
                )
            }
        }
    }
}

dependencies {
    "testImplementation"(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
