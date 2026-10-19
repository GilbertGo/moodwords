plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// 워치 앱과 폰 앱이 함께 쓰는 부분: 감정 단어 사전, 기록 저장, 앱 내 업데이트
android {
    namespace = "com.example.moodwords.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
