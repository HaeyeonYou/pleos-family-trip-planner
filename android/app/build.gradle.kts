plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.haeyeonyou.familytrip"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.haeyeonyou.familytrip"
        // 에뮬레이터 하나를 여러 작업이 나눠 쓸 때 서로 덮어쓰지 않게: -PappIdSuffix=.care
        (project.findProperty("appIdSuffix") as String?)?.let { applicationIdSuffix = it }
        // Pleos Playground requires API 28+ (signing key rotation / lineage)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 코스 서버 주소. 에뮬레이터에서 Mac의 로컬 서버는 10.0.2.2.
        // NAS 배포 후에는 -PserverUrl=https://... 로 바꿔 빌드한다.
        val serverUrl = project.findProperty("serverUrl") as String? ?: "http://10.0.2.2:8000/"
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
        buildConfig = true
    }
    testOptions {
        // 단위 테스트에서 android.util.Log 같은 호출이 있어도 죽지 않게 한다 (동기화 규칙의 실패 경로 테스트).
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    testImplementation("junit:junit:4.13.2")
    debugImplementation(libs.androidx.ui.tooling)
}
