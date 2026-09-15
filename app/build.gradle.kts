plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.live.finance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.live.finance"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-wave0"
    }

    // fake = 本地假数据（无需后端即可跑通主干）；prod = 连真实后端。
    // Sync 后 Build Variants 面板会出现 app/fake / app/prod 变体。
    flavorDimensions += "mode"
    productFlavors {
        create("fake") {
            dimension = "mode"
            applicationIdSuffix = ".fake"
            versionNameSuffix = "-fake"
            buildConfigField("boolean", "USE_FAKE", "true")
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:666/\"")
        }
        create("prod") {
            dimension = "mode"
            buildConfigField("boolean", "USE_FAKE", "false")
            // 真机联调默认指向局域网后端；如用 Android 模拟器访问本机后端改成 http://10.0.2.2:666/
            buildConfigField("String", "BASE_URL", "\"http://192.168.0.103:666/\"")
        }
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 复刻底座（自带 compose-bom / coil / activity-compose 通过 api 传递）
    implementation(project(":vant-ui"))

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 网络（Gson 转换器最稳；避免额外编译器插件）
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 本地存储（token / 指纹 / 会话）
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
