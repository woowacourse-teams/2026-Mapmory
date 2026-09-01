plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.mapmory.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }
    androidLibrary {
        androidResources.enable = true
    }
    jvm()
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:${libs.versions.composeMultiplatform.get()}")
            implementation(libs.jetbrains.androidx.navigation.compose)
            implementation(libs.jetbrains.androidx.lifecycle.viewmodel.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0") // Use the latest version
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.room.runtime)
            implementation("androidx.activity:activity-compose:1.11.0")
            implementation("androidx.exifinterface:exifinterface:1.4.1")
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    androidRuntimeClasspath("org.jetbrains.compose.ui:ui-tooling:${libs.versions.composeMultiplatform.get()}")
}

// Compose 1.11.1 does not initialize the output for the newly added KMP device-test variant.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach {
    val outputDirectory = javaClass.getMethod("getOutputDirectory").invoke(this)
        as org.gradle.api.file.DirectoryProperty
    outputDirectory.set(layout.buildDirectory.dir("generated/compose/$name"))
}
