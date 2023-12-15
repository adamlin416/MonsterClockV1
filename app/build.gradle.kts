plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.monsterclockv1"
    compileSdk = 34

    signingConfigs {
        create("release") {
            keyAlias = "key1" // 替換為您的密鑰別名
            keyPassword = "jack4160" // 替換為您的密鑰密碼
            storeFile = file("C:\\Users\\Adam\\Documents\\AndroidKeyStore\\key_test_release_1.jks") // 替換為您的 Keystore 文件的路徑
            storePassword = "jack4160" // 替換為您的 Keystore 密碼
        }
    }

    defaultConfig {
        applicationId = "com.example.monsterclockv1"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas".toString())
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Room
    val roomVersion = "2.5.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // coroutine
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    // ThreeTenABP
    implementation ("com.jakewharton.threetenabp:threetenabp:1.3.1")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // glide
    implementation("com.github.bumptech.glide:glide:4.12.0")
    kapt("com.github.bumptech.glide:compiler:4.12.0")
    // spotlight
    implementation("com.github.takusemba:spotlight:2.0.0")
    // gson
    implementation("com.google.code.gson:gson:2.8.9")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation ("androidx.activity:activity-ktx:1.4.0")
}