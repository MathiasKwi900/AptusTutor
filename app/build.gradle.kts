plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hiltAndroid)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.nexttechtitan.aptustutor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexttechtitan.aptustutor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Crucial for preventing compression of the model file
    androidResources {
        noCompress.add("task")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    // Core Android & Jetpack Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt for Dependency Injection
    implementation(libs.hiltAndroid)
    kapt(libs.hiltAndroidCompiler)
    implementation(libs.hiltComposeNavigation)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    // Room for Local Database
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // WorkManager for Background Tasks (Model Download)
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase (Authentication for Teacher & Storage for Model)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.storage.ktx)

    // Google AI Edge (Gemma 3n)
    implementation(libs.mediapipe.tasks.genai)

    // Nearby Connections for Offline Sharing
    implementation(libs.play.services.nearby)

    // Gson for Serialization
    implementation(libs.gson)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.core.splashscreen)
    // --- End of Added Dependencies ---

    // Utilities
    implementation(libs.androidx.datastore.preferences) // For saving user role (Tutor/Student)
    implementation(libs.gson) // For serializing data for Nearby Connections
    implementation(libs.coil.compose) // For displaying captured images
    implementation(libs.accompanist.permissions) // For handling Bluetooth/Wi-Fi permissions

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}