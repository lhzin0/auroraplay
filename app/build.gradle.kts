plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

android {
    namespace = "com.auroraplay.iptv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.auroraplay.iptv"
        minSdk = 24
        targetSdk = 36
        // Versioning convention:
        //   x.x.PATCH  -> bug fixes only
        //   x.MINOR.x  -> larger updates (new features, redesigns)
        // versionCode increments monotonically on every release.
        versionCode = 49
        versionName = "1.24.0"
        // Default application credential for automatic metadata and official
        // trailers. It is intentionally kept out of the settings UI.
        buildConfigField("String", "TMDB_API_KEY", "\"b89b1d7fe6f14b4825f390e3db639b16\"")

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.16.0")
    // BiometricPrompt needs a FragmentActivity to host it (see MainActivity).
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
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
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Security (encrypted prefs for credentials)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

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

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Extracts the dominant color of a poster so the hero glow can be tinted
    // by the artwork itself rather than a fixed accent.
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
