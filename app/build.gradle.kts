plugins { id("com.android.application") }

android {
    namespace = "com.hgikvt.fizak"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hgikvt.fizak"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies { implementation("androidx.core:core:1.15.0") }
