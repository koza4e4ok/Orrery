plugins {
    id("com.vanniktech.maven.publish")
    // Tells klibs.io about each Maven Central release (finalizes publishToMavenCentral).
    id("org.jetbrains.kotlin.klibs-io-notifier")
    id("org.jetbrains.dokka")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // Sign only when a key is provided (CI); keeps publishToMavenLocal credential-less.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

// @InternalOrreryApi declarations live in *.internal packages and are not documented (#89).
dokka {
    dokkaSourceSets.configureEach {
        perPackageOption {
            matchingRegex.set("""me\.kozakov\.orrery(\.\w+)*\.internal(\..*)?""")
            suppress.set(true)
        }
    }
}
