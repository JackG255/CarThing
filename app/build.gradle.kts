plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

import java.util.Properties

/**
 * Release signing: from `keystore.properties` in the project root (never committed), or from
 * environment variables in CI. Without either, release builds are left unsigned.
 */
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    System.getenv("CARTHING_KEYSTORE")?.let { path ->
        setProperty("storeFile", path)
        setProperty("storePassword", System.getenv("CARTHING_KEYSTORE_PASSWORD"))
        setProperty("keyAlias", System.getenv("CARTHING_KEY_ALIAS"))
        setProperty("keyPassword", System.getenv("CARTHING_KEY_PASSWORD"))
    }
}


android {
    namespace = "com.carthing"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carthing"
        minSdk = 26
        targetSdk = 35
        // Release builds from CI take the version from the git tag (v1.2.3 -> "1.2.3", code 10203);
        // local builds stay at 0.1.0 / 1. Codes must only ever go up for updates to install.
        val tagVersion = System.getenv("CARTHING_VERSION")?.removePrefix("v")
        val parts = tagVersion?.split('.')?.map { it.toIntOrNull() ?: error("Bad version tag: $tagVersion") }
        require(parts == null || (parts.size == 3 && parts[1] < 100 && parts[2] < 100)) { "Version tags look like v1.2.3" }
        versionCode = parts?.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch } ?: 1
        versionName = tagVersion ?: "0.1.0"
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            // Installs next to the release app, so testing never touches real data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // One APK per CPU type: the bundled text-recognition model ships native code for each,
    // which is most of a universal APK's size. Phones from the last decade use arm64-v8a.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // Exported Room schemas for migration tests; Robolectric reads them from the app's debug assets.
    sourceSets["debug"].assets.srcDir("$projectDir/schemas")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
