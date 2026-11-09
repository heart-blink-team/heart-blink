import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Supabase URL and anon key live in local.properties (gitignored). Empty values mean the app runs offline.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = (localProps.getProperty(key) ?: "").trim().replace("\"", "")

android {
    namespace = "kr.heartblink.car"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.heartblink.car"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/previous-compilation-data.bin")
        }
    }
    useLibrary("android.car")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // hand-drawn illustrations and icons come from Figma as SVG files in assets/figma
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")

    // Supabase backend. 3.0.2 is the last release built with Kotlin 2.0.21 (3.0.3+ needs Kotlin 2.1).
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.2"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    // OkHttp engine: no Google Play services needed, and it supports the websockets realtime uses
    implementation("io.ktor:ktor-client-okhttp:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Pleos Vehicle SDK: gear (driving), wiper and lights
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Pleos SpeechToText: writes down a voice message while it is recorded
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // Pleos TextToSpeech: talks to the driver while the screen is locked in gear D
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    // Pleos NaviHelper: arrival at the destination, the car's place, the next turn, bookmarks
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    // MapLibre with OpenFreeMap tiles: the pin map for place messages (no key)
    implementation("org.maplibre.gl:android-sdk:11.13.5")

    testImplementation("junit:junit:4.13.2")
}
