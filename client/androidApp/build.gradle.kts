import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.mapmory.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mapmory.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 4
        versionName = "0.1.3"
        resValue(
            type = "string",
            name = "mapmory_api_base_url",
            value = localProperties.getProperty("MAPMORY_API_BASE_URL").orEmpty(),
        )
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
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
    implementation(libs.ktor.client.core)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${libs.versions.androidxCompose.get()}")
    debugImplementation("androidx.compose.ui:ui-test-manifest:${libs.versions.androidxCompose.get()}")
}
