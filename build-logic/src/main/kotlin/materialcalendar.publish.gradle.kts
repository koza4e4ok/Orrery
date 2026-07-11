plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // Sign only when a key is provided (CI); keeps publishToMavenLocal credential-less.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}
