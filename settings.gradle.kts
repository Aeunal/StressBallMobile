pluginManagement {
    // Maven Central first: everything :core needs lives there, so :core builds
    // even where Google's repository is unreachable.
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "StressBallMobile"

// The pure-Kotlin game engine builds anywhere with a JDK.
include(":core")

// The Android app needs an Android SDK. Skip it when none is configured so
// `./gradlew :core:test` still works on machines without the SDK (e.g. plain CI
// runners or sandboxes). Set ANDROID_HOME / ANDROID_SDK_ROOT or add
// `sdk.dir=` to local.properties to enable it.
val localProps = java.util.Properties().apply {
    val f = rootDir.resolve("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val sdkConfigured = localProps.getProperty("sdk.dir") != null ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (sdkConfigured) {
    include(":app")
} else {
    logger.lifecycle("Android SDK not found; skipping :app module (only :core is configured).")
}
