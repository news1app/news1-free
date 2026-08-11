plugins {
    id("com.android.application")
}

android {
    namespace = "com.news1.market"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.news1.market"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0-free"
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime:2.9.1")
}
