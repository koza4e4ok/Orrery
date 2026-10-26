import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    // Loaded once here (apply false) so the convention plugin's shared
    // MavenCentralBuildService resolves in a single classloader scope across
    // sibling modules instead of failing with a cross-project provider error.
    alias(libs.plugins.maven.publish) apply false
    // Applied here to aggregate the published modules' API docs into one site:
    // ./gradlew :dokkaGeneratePublicationHtml -> build/dokka/html
    alias(libs.plugins.dokka)
}

dokka {
    moduleName.set("Orrery")
}

dependencies {
    dokka(project(":orrery-core"))
    dokka(project(":orrery-lunar"))
    dokka(project(":orrery-compose"))
}

// Node.js, Yarn and Binaryen for the wasmJs target resolve from the repositories in
// settings.gradle.kts; a null base URL stops the Kotlin plugin adding its own.
allprojects {
    plugins.withType<WasmNodeJsPlugin> {
        the<WasmNodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<WasmYarnPlugin> {
        the<WasmYarnRootEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<BinaryenPlugin> {
        the<BinaryenEnvSpec>().downloadBaseUrl.set(null as String?)
    }
}
