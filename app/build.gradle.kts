plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.moodwords"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.moodwords"
        minSdk = 30          // Wear OS 3 이상 (갤럭시워치4 이후)
        targetSdk = 34
        // GitHub 빌드 번호 = 버전 번호 = Releases 태그(build-N). 앱 업데이트 확인에 사용
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    // 모든 빌드를 같은 키로 서명해야 덮어쓰기 업데이트가 됨.
    // 키 파일은 저장소에, 비밀번호는 GitHub Secrets(KEYSTORE_PASSWORD)에 보관
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
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation("androidx.health:health-services-client:1.0.0")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")
}
