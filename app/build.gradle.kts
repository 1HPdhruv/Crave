import java.util.Base64
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


// Read through a Gradle provider (not plain file I/O) so the configuration cache is invalidated whenever
// local.properties changes; otherwise a stale cache silently bakes an EMPTY staging URL into the APK.
val localProperties = Properties()
providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull?.let {
    localProperties.load(it.reader())
}

// ── Supabase environment separation ─────────────────────────────────────────
// Each flavor is pinned to one Supabase project and verified at build time:
//   * staging    -> CRAVE-Staging. Source, first match wins: env CRAVE_STAGING_URL/CRAVE_STAGING_ANON_KEY,
//                   then STAGING_SUPABASE_URL/STAGING_SUPABASE_ANON_KEY, then SUPABASE_URL/SUPABASE_ANON_KEY
//                   ONLY when that URL is the staging project. Never falls back to production.
//   * production -> production project only (SUPABASE_URL/SUPABASE_ANON_KEY); a build with any other project fails.
//   * mock       -> SUPABASE_URL/SUPABASE_ANON_KEY as-is (local mock backend; not pinned).
// Only client-safe keys may be embedded: service_role JWTs and sb_secret_ keys fail the build.
val stagingProjectRef = "yatbgxfjlnqpvrftjdmu"
val productionProjectRef = "btdmhveaqssuuhyoyanz"

fun projectRefOf(url: String): String =
    Regex("^https://([a-z0-9]+)\\.supabase\\.co/?$").find(url)?.groupValues?.get(1) ?: ""

/** Decoded (role, ref) of a legacy JWT key, or null for non-JWT keys (sb_publishable_…). Values are never printed. */
fun jwtClaims(key: String): Pair<String?, String?>? {
    if (!key.startsWith("eyJ")) return null
    return runCatching {
        val payload = String(Base64.getUrlDecoder().decode(key.split(".")[1].trimEnd('=')))
        fun claim(n: String) = Regex("\"$n\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
        claim("role") to claim("ref")
    }.getOrElse { null to null }
}

/** Returns a reason the key must not be embedded with this URL, or null when it's acceptable. */
fun keyProblem(url: String, key: String): String? {
    if (key.isBlank()) return null
    if (key.startsWith("sb_secret_")) return "is a SECRET key (sb_secret_); only publishable/anon keys may ship in the app"
    val claims = jwtClaims(key) ?: return null
    if (claims.first == "service_role") return "is a service_role key; only publishable/anon keys may ship in the app"
    val ref = projectRefOf(url)
    if (claims.second != null && ref.isNotEmpty() && claims.second != ref) return "belongs to a different Supabase project than its URL"
    return null
}

fun env(name: String): String? = providers.environmentVariable(name).orNull?.trim()?.takeIf { it.isNotEmpty() }
fun prop(name: String): String? = localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val defaultSupabaseUrl = prop("SUPABASE_URL")?.trimEnd('/') ?: ""
val defaultSupabaseAnonKey = prop("SUPABASE_ANON_KEY") ?: ""

// URL and key always come from the SAME source so a key can't be paired with another project's URL.
val stagingSource: Pair<String, String> = listOf(
    env("CRAVE_STAGING_URL") to env("CRAVE_STAGING_ANON_KEY"),
    prop("STAGING_SUPABASE_URL") to prop("STAGING_SUPABASE_ANON_KEY"),
    defaultSupabaseUrl.takeIf { projectRefOf(it) == stagingProjectRef } to defaultSupabaseAnonKey.takeIf { it.isNotEmpty() }
).firstOrNull { (u, k) -> u != null && k != null }
    ?.let { (u, k) -> u!!.trimEnd('/') to k!! }
    ?.takeIf { (u, _) -> projectRefOf(u) == stagingProjectRef }   // a non-staging URL is discarded: fail closed
    ?: ("" to "")
val stagingSupabaseUrl = stagingSource.first
val stagingSupabaseAnonKey = stagingSource.second

val requestedTasks = gradle.startParameter.taskNames.map { it.substringAfterLast(":").lowercase() }.filter { it != "clean" }
val buildsStaging = requestedTasks.isEmpty() || requestedTasks.any { "staging" in it || !("mock" in it || "production" in it) }
val buildsProduction = requestedTasks.any { "production" in it } ||
    requestedTasks.any { it in setOf("build", "assemble", "assemblerelease", "bundle", "bundlerelease") }

keyProblem(stagingSupabaseUrl, stagingSupabaseAnonKey)?.let { error("Staging Supabase key $it.") }
keyProblem(defaultSupabaseUrl, defaultSupabaseAnonKey)?.let { error("SUPABASE_ANON_KEY $it.") }
if (buildsProduction) {
    check(projectRefOf(defaultSupabaseUrl) == productionProjectRef) {
        "Production build requires SUPABASE_URL to be the production project; local.properties points elsewhere " +
            "(currently '${projectRefOf(defaultSupabaseUrl).ifEmpty { "missing/invalid" }}'). Build a staging variant instead."
    }
    check(defaultSupabaseAnonKey.isNotEmpty()) { "SUPABASE_ANON_KEY is missing from local.properties" }
}
if (buildsStaging && stagingSupabaseUrl.isEmpty()) {
    println("[WARNING] No CRAVE-Staging Supabase config found (CRAVE_STAGING_URL/KEY, STAGING_SUPABASE_URL/KEY, or " +
        "SUPABASE_URL pointing at the staging project). The staging APK will refuse to start. Production is never used as a fallback.")
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
        // SUPABASE_URL / SUPABASE_ANON_KEY / SUPABASE_KEY_PREFIX are set per flavor below.
        buildConfigField("String", "EXPECTED_SUPABASE_REF", "\"\"")
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
            buildConfigField("String", "SUPABASE_URL", "\"${defaultSupabaseUrl}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${defaultSupabaseAnonKey}\"")
            buildConfigField("String", "SUPABASE_KEY_PREFIX", "\"${defaultSupabaseAnonKey.take(4)}\"")
        }
        create("staging") {
            dimension = "environment"
            versionNameSuffix = "-staging"
            // stagingDebug = real Supabase + real repositories, no local backend
            buildConfigField("String", "BASE_URL", "\"https://staging-api.gag.srmist.edu.in/\"")
            buildConfigField("Boolean", "USE_MOCK", "false")
            buildConfigField("String", "SUPABASE_URL", "\"${stagingSupabaseUrl}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${stagingSupabaseAnonKey}\"")
            buildConfigField("String", "SUPABASE_KEY_PREFIX", "\"${stagingSupabaseAnonKey.take(4)}\"")
            buildConfigField("String", "EXPECTED_SUPABASE_REF", "\"${stagingProjectRef}\"")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://api.gag.srmist.edu.in/\"")
            buildConfigField("Boolean", "USE_MOCK", "false")
            buildConfigField("String", "SUPABASE_URL", "\"${defaultSupabaseUrl}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${defaultSupabaseAnonKey}\"")
            buildConfigField("String", "SUPABASE_KEY_PREFIX", "\"${defaultSupabaseAnonKey.take(4)}\"")
            buildConfigField("String", "EXPECTED_SUPABASE_REF", "\"${productionProjectRef}\"")
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

    testOptions {
        // JVM unit tests call code that logs through android.util.Log; return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
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
