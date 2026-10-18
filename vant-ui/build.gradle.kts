plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.live.vant"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.animation:animation")
    api("androidx.activity:activity-compose:1.9.0")
    // Coil 用于 VanImage / VanUploader / VanImagePreview 的网络与本地图片加载（对应浏览器 <img> 能力）
    api("io.coil-kt:coil-compose:2.7.0")
    // 银行 logo 在库里全是 .svg（api/public/uploads/bank/**.svg，23 个），而 coil-compose 2.7 默认
    //   **不带 SVG 解码器** ⇒ 原生里所有银行 logo 必然加载失败（浏览器 `<img>` 能直接渲染）。
    //   负责人 2026-09-19 拍板「加 SVG 解码」⇒ 补 coil-svg，并在 App.onCreate 注册 ImageUtils.setDecoder。
    api("io.coil-kt:coil-svg:2.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
