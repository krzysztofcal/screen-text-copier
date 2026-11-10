plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.screentextcopier"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.screentextcopier"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = (project.findProperty("RELEASE_KEYSTORE_PATH") as? String)
        ?: System.getenv("RELEASE_KEYSTORE_PATH")
    val releaseKeystorePassword = (project.findProperty("RELEASE_KEYSTORE_PASSWORD") as? String)
        ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
    val releaseKeyAlias = (project.findProperty("RELEASE_KEY_ALIAS") as? String)
        ?: System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = (project.findProperty("RELEASE_KEY_PASSWORD") as? String)
        ?: System.getenv("RELEASE_KEY_PASSWORD")

    val hasReleaseSigning = !releaseKeystorePath.isNullOrEmpty() &&
            !releaseKeystorePassword.isNullOrEmpty() &&
            !releaseKeyAlias.isNullOrEmpty() &&
            !releaseKeyPassword.isNullOrEmpty() &&
            file(releaseKeystorePath).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    testImplementation("junit:junit:4.13.2")
}
