plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.goodnewsguys.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.goodnewsguys.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Signed with the debug key so the APK can be sideloaded. Use a real key before any store upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("gng.digests", rootProject.file("../data/digests").absolutePath)
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // Screenshot tests: render the screens on the PC, no phone or emulator needed.
    testImplementation(platform("androidx.compose:compose-bom:2025.09.00"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.50.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.50.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
