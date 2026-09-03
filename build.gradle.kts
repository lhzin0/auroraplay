// Top-level build file
plugins {
    id("com.android.application") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    // Since Kotlin 2.0 the Compose compiler ships with Kotlin and is applied
    // as its own plugin (versioned in lockstep with Kotlin) — this replaces
    // the old composeOptions { kotlinCompilerExtensionVersion } in :app.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.dagger.hilt.android") version "2.57" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
