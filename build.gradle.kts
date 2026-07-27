// Top-level build file. Plugin versions are declared here and applied in :app.
//
// AGP 8.9.1 is the minimum that supports compileSdk 36 (Android 16), which
// Google Play requires for new app submissions from 31 August 2026.
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
