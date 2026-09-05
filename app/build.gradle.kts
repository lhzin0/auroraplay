import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    version.set("1.3.1")
    // The codebase predates this setup, so `ktlintCheck` finds thousands of
    // pre-existing style violations (wildcard imports, long lambdas, line
    // length) — reformatting all of that in one pass would be a huge,
    // hard-to-review diff for zero functional benefit. Report, don't fail:
    // this stays available as `./gradlew ktlintCheck` / `ktlintFormat` for
    // new/touched code, without blocking the existing build or CI on
    // pre-existing style debt.
    ignoreFailures.set(true)
}

// The plugin wires ktlintCheck into `check` by default; skip that so a
// plain `./gradlew check` (and CI) isn't slowed down re-scanning the whole
// codebase on every run — run it explicitly when you want it.
tasks.matching { it.name == "check" }.configureEach {
    setDependsOn(dependsOn.filterNot { it.toString().contains("ktlint", ignoreCase = true) })
}

val developerProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.reader(Charsets.UTF_8).use { load(it) }
}
fun localBuildString(name: String): String {
    val value = System.getenv(name) ?: developerProperties.getProperty(name, "")
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}

android {
    namespace = "com.auroraplay.iptv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.auroraplay.iptv"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Versioning convention:
        //   x.x.PATCH  -> bug fixes only
        //   x.MINOR.x  -> larger updates (new features, redesigns)
        // versionCode increments monotonically on every release.
        versionCode = 94
        versionName = "1.38.0"
        // Default application credential for automatic metadata and official
        // trailers. It is intentionally kept out of the settings UI.
        buildConfigField("String", "TMDB_API_KEY", localBuildString("TMDB_API_KEY"))

        // Debug-only playlist seed (see DebugConnectionSeeder). Blank here so
        // the release build carries no credentials at all; the debug build
        // type below fills them in.
        buildConfigField("String", "SEED_XTREAM_NAME", "\"\"")
        buildConfigField("String", "SEED_XTREAM_URL", "\"\"")
        buildConfigField("String", "SEED_XTREAM_USER", "\"\"")
        buildConfigField("String", "SEED_XTREAM_PASS", "\"\"")

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Deliberately unsigned here. scripts/build-release.ps1 signs and verifies
            // the final APK with the production key and the migration lineage.
            isDebuggable = false
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // Pre-loads this Xtream playlist on a fresh debug install so
            // testing skips the onboarding flow. Never present in release.
            buildConfigField("String", "SEED_XTREAM_NAME", localBuildString("SEED_XTREAM_NAME"))
            buildConfigField("String", "SEED_XTREAM_URL", localBuildString("SEED_XTREAM_URL"))
            buildConfigField("String", "SEED_XTREAM_USER", localBuildString("SEED_XTREAM_USER"))
            buildConfigField("String", "SEED_XTREAM_PASS", localBuildString("SEED_XTREAM_PASS"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // No composeOptions block: with Kotlin 2.x the Compose compiler is supplied
    // by the org.jetbrains.kotlin.plugin.compose plugin (applied above), not by
    // a kotlinCompilerExtensionVersion pin.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xannotation-default-target=param-property"
        )
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }

    // Exported Room schemas (app/schemas/) are bundled into the instrumented
    // test APK so MigrationTestHelper can validate every migration.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

// Room exports the current schema version to app/schemas/ on every build. These
// JSON files are committed and used by the migration tests. exportSchema was
// turned on at DB version 7, so pre-7 schemas are not available — pre-7
// upgrades still run through the hand-written MIGRATION_x_y objects.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.16.0")
    // BiometricPrompt needs a FragmentActivity to host it (see MainActivity).
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    // collectAsStateWithLifecycle — pause UI-state collection while backgrounded (audit #22)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57")
    ksp("com.google.dagger:hilt-android-compiler:2.57")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    androidTestImplementation("androidx.room:room-testing:2.7.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Security (encrypted prefs for credentials)
    implementation("androidx.security:security-crypto:1.1.0")

    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.0")
    implementation("androidx.media3:media3-cast:1.11.0")
    implementation("com.google.android.gms:play-services-cast-framework:22.0.0")
    implementation("androidx.media3:media3-exoplayer-workmanager:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.media3:media3-database:1.11.0")

    // Backdrop blur — real frosted glass on API 31+ (RenderEffect), graceful
    // tint-only scrim below that. Powers FrostGlass on the floating surfaces
    // (bottom nav bar, the player's ⋮ panel).
    implementation("dev.chrisbanes.haze:haze:1.6.10")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Extracts the dominant color of a poster so the hero glow can be tinted
    // by the artwork itself rather than a fixed accent.
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // ViewModel tests need a substitute for Dispatchers.Main (viewModelScope
    // uses it) and virtual-time control over delay()/debounce().
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
