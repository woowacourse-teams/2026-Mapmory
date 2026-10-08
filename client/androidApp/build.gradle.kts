import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val debugApiBaseUrl = localProperties
    .getProperty("MAPMORY_DEBUG_API_BASE_URL")
    ?.takeIf(String::isNotBlank)
    ?: "https://dev-api.map-mory.com/api/v1"
val releaseApiBaseUrl = "https://api.map-mory.com/api/v1"
val internalStoreFile = providers.environmentVariable("MAPMORY_INTERNAL_STORE_FILE").orNull
val internalStorePassword = providers.environmentVariable("MAPMORY_INTERNAL_STORE_PASSWORD").orNull
val internalKeyAlias = providers.environmentVariable("MAPMORY_INTERNAL_KEY_ALIAS").orNull
val internalKeyPassword = providers.environmentVariable("MAPMORY_INTERNAL_KEY_PASSWORD").orNull
val hasInternalSigning = listOf(internalStoreFile, internalStorePassword, internalKeyAlias, internalKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.mapmory.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mapmory.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 9
        versionName = "0.1.7"
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    signingConfigs {
        if (hasInternalSigning) {
            create("internal") {
                storeFile = file(requireNotNull(internalStoreFile))
                storePassword = internalStorePassword
                keyAlias = internalKeyAlias
                keyPassword = internalKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            resValue(
                type = "string",
                name = "mapmory_api_base_url",
                value = debugApiBaseUrl,
            )
        }
        create("internal") {
            initWith(getByName("release"))
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.findByName("internal")
            resValue("string", "mapmory_api_base_url", debugApiBaseUrl)
        }
        release {
            isMinifyEnabled = false
            resValue(
                type = "string",
                name = "mapmory_api_base_url",
                value = releaseApiBaseUrl,
            )
        }
    }
}

tasks.matching { it.name == "processInternalGoogleServices" }.configureEach {
    enabled = false
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation(libs.androidx.compose.foundation)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:${libs.versions.androidxCompose.get()}")
    debugImplementation("androidx.compose.ui:ui-tooling:${libs.versions.androidxCompose.get()}")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.ktor.client.core)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${libs.versions.androidxCompose.get()}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${libs.versions.androidxCompose.get()}")
}
