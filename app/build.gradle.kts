plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cak3d.biscuitmesh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cak3d.biscuitmesh"
        minSdk = 26
        targetSdk = 36
        versionCode = 98
        versionName = "0.81.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing is driven by env vars (set by CI from GitHub secrets). When they
    // are absent — e.g. a local build with no keystore — release falls back to the debug
    // signing key so the APK still installs for testing.
    val hasReleaseSigning = System.getenv("KEYSTORE_FILE")?.let { file(it).exists() } == true
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_FILE")!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.osmdroid.android)
    implementation(libs.play.services.location)
    debugImplementation(libs.androidx.ui.tooling)
}
