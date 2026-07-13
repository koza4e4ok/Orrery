plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    // Loaded once here (apply false) so the convention plugin's shared
    // MavenCentralBuildService resolves in a single classloader scope across
    // sibling modules instead of failing with a cross-project provider error.
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}
