plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val supabaseUrl = providers.gradleProperty("SUPABASE_URL").orElse("").get().trim().trimEnd('/')
val supabaseKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY").orElse("").get().trim()
require(supabaseUrl.isEmpty() || (supabaseUrl.startsWith("https://") && !supabaseUrl.contains('"') && !supabaseUrl.contains('\\'))) { "SUPABASE_URL must be an HTTPS URL" }
require(!supabaseKey.contains('"') && !supabaseKey.contains('\\')) { "Invalid key" }
android {
    namespace = "org.studentmodulehub.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.studentmodulehub.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "0.5.0"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("ANDROID_KEYSTORE_PATH") ?: "missing-release-key.jks")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "studentmodulehub"
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}