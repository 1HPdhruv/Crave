import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp) 
    alias(libs.plugins.google.services)
}


val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val supabaseUrl = localProperties.getProperty("SUPABASE_URL")?.trim() ?: ""
val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY")?.trim() ?: ""

check(supabaseUrl.isNotBlank()) { "SUPABASE_URL is missing from local.properties" }
check(supabaseAnonKey.isNotBlank()) { "SUPABASE_ANON_KEY is missing from local.properties" }

// Warn at build time if the key looks wrong (legacy JWT starts with eyJ)
if (supabaseAnonKey.isNotBlank() && !supabaseAnonKey.startsWith("eyJ")) {
    println("[WARNING] SUPABASE_ANON_KEY does not look like a legacy JWT (should start with 'eyJ').")
    println("[WARNING] Go to Supabase Dashboard → Project Settings → API → anon public key.")
    println("[WARNING] Key prefix found: ${supabaseAnonKey.take(12)}...")
}

android {
    namespace = "com.srmfood.gag"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.srmfood.gag"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.srmfood.gag.HiltTestRunner"

        // BuildConfig fields
        buildConfigField("String", "BASE_URL", "\"https://api.gag.srmist.edu.in/\"")
        buildConfigField("String", "API_VERSION", "\"v1\"")
        buildConfigField("Boolean", "USE_MOCK", "false")
        buildConfigField("Boolean", "ENABLE_LOGGING", "true")
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseAnonKey}\"")
        // Safe debug field: first 4 chars only, never the full key
        buildConfigField("String", "SUPABASE_KEY_PREFIX", "\"${supabaseAnonKey.take(4)}\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            // BASE_URL and USE_MOCK are NOT set here so product flavors control them.
            // mockDebug flavor overrides USE_MOCK=true and BASE_URL=localhost.
            // stagingDebug flavor uses USE_MOCK=false and real Supabase.
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://api.gag.srmist.edu.in/\"")
            buildConfigField("Boolean", "USE_MOCK", "false")
            buildConfigField("Boolean", "ENABLE_LOGGING", "false")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("mock") {
            dimension = "environment"
            versionNameSuffix = "-mock"
            // mockDebug = local backend + mock repositories
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("Boolean", "USE_MOCK", "true")
        }
        create("staging") {
            dimension = "environment"
            versionNameSuffix = "-staging"
            // stagingDebug = real Supabase + real repositories, no local backend
            buildConfigField("String", "BASE_URL", "\"https://staging-api.gag.srmist.edu.in/\"")
            buildConfigField("Boolean", "USE_MOCK", "false")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://api.gag.srmist.edu.in/\"")
            buildConfigField("Boolean", "USE_MOCK", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Desugar
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.kotlinx.serialization)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Supabase
    implementation(libs.supabase.gotrue)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.compose.auth)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil
    implementation(libs.coil.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.analytics.ktx)

    // Security
    implementation(libs.security.crypto)

    // ML Kit Barcode Scanning
    implementation(libs.mlkit.barcode.scanning)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ZXing (QR Generation)
    implementation(libs.zxing.android.embedded)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Lottie
    implementation(libs.lottie.compose)

    // Razorpay
    implementation(libs.razorpay.checkout)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
