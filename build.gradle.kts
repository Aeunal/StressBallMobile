// Top-level build file. JetBrains plugins are declared here (apply false) so
// that sub-projects share one version from gradle/libs.versions.toml.
//
// The Android Gradle Plugin is deliberately NOT declared here: it is only
// applied by :app, so that a machine without access to Google's Maven
// repository (or without an Android SDK) can still build and test :core.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
