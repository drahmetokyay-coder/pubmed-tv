plugins {
    id("com.android.application")
}

android {
    namespace = "com.ahmetokyay.pubmedtv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ahmetokyay.pubmedtv"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
}
