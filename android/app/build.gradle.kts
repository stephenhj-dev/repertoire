import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.oslo.repertoire"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.oslo.repertoire"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // TMDB v4 read access token from tmdb.properties (gitignored). Empty when the file is
        // missing; the app then hides everything that needs TMDB.
        val tmdbProps = Properties().apply {
            val f = rootProject.file("tmdb.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val tmdbToken = tmdbProps.getProperty("tmdb.readToken", "").trim()
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "TMDB_READ_TOKEN", "\"$tmdbToken\"")
    }

    // Shared signing key for debug and release builds, kept outside the repo.
    // keystore.properties (gitignored) points at it; builds fall back to the
    // default debug key when it's missing.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        signingConfigs.findByName("upload")?.let { upload ->
            getByName("debug") { signingConfig = upload }
            getByName("release") { signingConfig = upload }
        }
        release {
            isMinifyEnabled = false
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("dev.chrisbanes.haze:haze:1.2.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("dev.mobile:dadb:2.0.0")

    testImplementation("junit:junit:4.13.2")
    // Runs the Room migration SQL on a real SQLite in JVM tests.
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
