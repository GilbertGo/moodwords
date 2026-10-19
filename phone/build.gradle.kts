plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 워치 없는 사람도 써볼 수 있는 폰 버전. 심박 대신 에너지를 직접 물어봄.
android {
    namespace = "com.example.moodwords.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.moodwords.phone"
        minSdk = 26
        targetSdk = 34
        // 워치와 같은 빌드 번호 체계 (Releases 태그 phone-N)
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    // 워치와 같은 고정 키로 서명 (덮어쓰기 업데이트용)
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("signing/release.p12")
            storeType = "pkcs12"
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "moodwords"
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
