// Top-level build file.
//
// Deliberately empty: every module declares its own plugins (with versions
// taken from gradle/libs.versions.toml) so that each plugin set is loaded in
// that module's classloader. The Kotlin Android plugin must be loaded together
// with the Android Gradle Plugin, and keeping AGP out of the root build lets
// :core build on machines without access to Google's Maven repository.
