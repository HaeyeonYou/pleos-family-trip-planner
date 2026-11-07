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
        // Pleos Playground requires API 28+ (signing key rotation / lineage)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 코스 서버 주소. 에뮬레이터에서 Mac의 로컬 서버는 10.0.2.2.
        // NAS 배포 후에는 -PserverUrl=https://... 로 바꿔 빌드한다.
        val serverUrl = project.findProperty("serverUrl") as String? ?: "http://10.0.2.2:8000/"
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")

        // 카카오맵 네이티브 앱 키는 저장소 루트 .env에서 읽는다 (키 해시로 이 앱에서만 쓸 수 있음).
        val rootEnv = rootProject.file("../.env")
        val kakaoNativeKey = if (rootEnv.exists()) {
            rootEnv.readLines().firstOrNull { it.startsWith("KAKAO_NATIVE_APP_KEY=") }?.substringAfter("=")?.trim().orEmpty()
        } else {
            ""
        }
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeKey\"")
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kakao.map)
    debugImplementation(libs.androidx.ui.tooling)
}
