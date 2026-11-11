import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}


val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
// Read from environment variable first (GitHub Actions), fallback to local.properties
val tmdbApiKey: String = System.getenv("TMDB_API_KEY") ?: localProperties.getProperty("TMDB_API_KEY") ?: ""
val apiFootballKey: String = System.getenv("API_FOOTBALL_KEY") ?: localProperties.getProperty("API_FOOTBALL_KEY") ?: ""
    val openFootApiKey: String = System.getenv("OPENFOOT_API_KEY") ?: localProperties.getProperty("OPENFOOT_API_KEY") ?: ""
    val highlightlyApiKey: String = System.getenv("HIGHLIGHTLY_API_KEY") ?: localProperties.getProperty("HIGHLIGHTLY_API_KEY") ?: ""
val footballDataOrgKey: String = System.getenv("FOOTBALL_DATA_ORG_KEY") ?: localProperties.getProperty("FOOTBALL_DATA_ORG_KEY") ?: ""
val theSportsDbKey: String = System.getenv("THESPORTSDB_KEY") ?: localProperties.getProperty("THESPORTSDB_KEY") ?: "3"
val bzzoiroToken: String = System.getenv("BZZOIRO_API_TOKEN") ?: localProperties.getProperty("BZZOIRO_API_TOKEN") ?: ""

val runNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.saydlawy.sahab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saydlawy.sahab"
        minSdk = 23
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "API_FOOTBALL_KEY", "\"$apiFootballKey\"")
        buildConfigField("String", "OPENFOOT_API_KEY", "\"$openFootApiKey\"")
        buildConfigField("String", "HIGHLIGHTLY_API_KEY", "\"$highlightlyApiKey\"")
        buildConfigField("String", "FOOTBALL_DATA_ORG_KEY", "\"$footballDataOrgKey\"")
        buildConfigField("String", "THESPORTSDB_KEY", "\"$theSportsDbKey\"")
        buildConfigField("String", "BZZOIRO_API_TOKEN", "\"$bzzoiroToken\"")
    }

    val keystoreFile = rootProject.file("release-keystore.jks")
    val hasReleaseKeystore = keystoreFile.exists() && keystoreFile.length() > 0L

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "my_app_key"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    androidResources {
        noCompress += "sports_db"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
