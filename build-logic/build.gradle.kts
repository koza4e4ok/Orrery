plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(plugin(libs.plugins.kotlin.jvm))
    implementation(plugin(libs.plugins.android.library))
    implementation(plugin(libs.plugins.compose.compiler))
    implementation(plugin(libs.plugins.ktlint))
    implementation(plugin(libs.plugins.detekt))
    implementation(plugin(libs.plugins.maven.publish))
    implementation(plugin(libs.plugins.dokka))
}

fun plugin(provider: Provider<PluginDependency>): Provider<String> =
    provider.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
