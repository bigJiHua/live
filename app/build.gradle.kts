import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 站点品牌信息（等价 web/.env里的 VITE_BRAND_NAME / VITE_POWERED_BY / VITE_COPYRIGHT_START）：
// 构建期注入 BuildConfig，登录页页脚非演示模式下显示。
// ⚠ Gradle 自身按 ISO-8859-1 解析 gradle.properties，中文会乱码，所以这里显式用 UTF-8 重读一遍。
// 换品牌只需改 gradle.properties 的 SITE_* 三项，UI 代码零改动。
val siteProps = Properties().apply {
    val f = rootProject.file("gradle.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}
fun site(key: String, fallback: String): String {
    // 1) 优先读 APP/gradle.properties（见上方 siteProps 加载）
    val fromProps = siteProps.getProperty(key)?.takeIf { it.isNotBlank() }
    if (fromProps != null) return fromProps
    // 2) 兜底读同名环境变量：便于签名口令等敏感值留在本地 / CI，不进仓库
    //    （公开仓库场景：RELEASE_* 只设环境变量，gradle.properties 保持为空）
    val fromEnv = System.getenv(key)?.takeIf { it.isNotBlank() }
    if (fromEnv != null) return fromEnv
    return fallback
}

// ────────────────────────────────────────────────────────────────────
// 安全整改 P0-4（公网部署，2026-10-08）：传输安全配置
//
// ⚠ 上线前可选（2026-10-17 BYO-backend 后，构建期不再注入任何站点地址）：
//   API_HOST      —— 证书对应的域名（仅 Pinning / networkSecurityConfig 白名单用；留空不锁定）
//
// 关于 Pinning 的取舍（重要，不要盲目开启）：
//   若服务器用 **Let's Encrypt**，证书约 90 天轮换一次 —— 硬编码证书指纹
//   （API_PINNED_SHA256）会导致证书更新后**全网用户 APP 同时失效**，且必须发版才能恢复。
//   因此本工程默认**不启用 Pinning**，只做「强制 HTTPS + 禁止明文降级」：
//     · usesCleartextTraffic=false + networkSecurityConfig 只允许 https
//     · 这样已能防「明文传输」与「降级到 http」
//   只有在以下情况才填 API_PINNED_SHA256 开启 Pinning：
//     · 你用**自签名证书**或固定 CA 的证书（指纹长期不变）
//   留空即关闭（安全，但不做证书锁定）。
// ────────────────────────────────────────────────────────────────────
// 🚫 构建期不再注入任何站点地址（2026-10-17 定稿）。
//   本 APP 采用「用户自带后端（BYO-backend）」：API 地址与分享 URL 全部在运行时由用户输入
//   并持久化到 SiteStore。因此 **APK 内不得存在任何默认/占位地址**——
//   构建期 baseUrl 恒为空，AppConfig.baseUrl 初始值也是空串。
//   ⚠ 不要再恢复 API_BASE_URL / SITE_URL 这类 buildConfigField：一旦注入，分发给别人的
//     包就会连到开发者的局域网/占位地址。
val apiHost = site("API_HOST", "")             // 例：api.example.com；留空则不做域名白名单
val apiPinnedSha = site("API_PINNED_SHA256", "") // 例：sha256/AAAA...；留空则不启用 Pinning
val enablePinning = apiPinnedSha.isNotBlank()

if (apiHost.isBlank()) {
    logger.warn("⚠️ [安全] gradle.properties 未填 API_HOST —— networkSecurityConfig 将不做域名白名单。")
}

android {
    namespace = "com.live.finance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.live.finance"
        minSdk = 24
        targetSdk = 34
        // 版本号转正：对齐生产 H5 套壳 App（webview-APP/manifest.json）versionName 2.1.0。
        // 其 versionCode 20260805020100 为 14 位时间戳，超过 Android 32 位上限
        // （2,147,483,647）会编译失败，故用语义化编码 2.1.0 → 20100。
        versionCode = 20100
        versionName = "2.1.0"
        buildConfigField("String", "SITE_BRAND_NAME", "\"${site("SITE_BRAND_NAME", "Gold 财管")}\"")
        buildConfigField("String", "SITE_POWERED_BY", "\"${site("SITE_POWERED_BY", "BigJiHua")}\"")
        buildConfigField("String", "SITE_COPYRIGHT_START", "\"${site("SITE_COPYRIGHT_START", "2026")}\"")
        // ── 传输安全参数（供 AppConfig / ApiClient 使用）──
        buildConfigField("String", "API_HOST", "\"$apiHost\"")
        buildConfigField("String", "API_PINNED_SHA256", "\"$apiPinnedSha\"")
        buildConfigField("boolean", "ENABLE_PINNING", "$enablePinning")
        // 🚫 不注入 BASE_URL / SITE_URL：站点地址一律运行时输入（见文件上方说明）。
    }

    // ────────────────────────────────────────────────────────────────────
    // 签名配置（安全整改 P2-13）
    // ⚠ 密钥库文件（*.jks）**不入库**（已加进 APP/.gitignore）。
    //   首次配置用下面命令生成，然后把路径/口令写进 gradle.properties：
    //   keytool -genkeypair -v -keystore APP/release.jks -alias live \
    //           -keyalg RSA -keysize 2048 -validity 10000
    //   留空时 release 构建会**未签名**（可调试安装，但不能上架/分发）。
    // ────────────────────────────────────────────────────────────────────
    val keystorePath = site("RELEASE_KEYSTORE", "")
    signingConfigs {
        if (keystorePath.isNotBlank()) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = site("RELEASE_STORE_PASSWORD", "")
                keyAlias = site("RELEASE_KEY_ALIAS", "live")
                keyPassword = site("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    // fake = 本地假数据（无需后端即可跑通主干）；prod = 连真实后端。
    // Sync 后 Build Variants 面板会出现 app/fake / app/prod 变体。
    flavorDimensions += "mode"
    productFlavors {
        create("fake") {
            dimension = "mode"
            applicationIdSuffix = ".fake"
            versionNameSuffix = "-fake"
            // 2026-09-18 负责人要求：两个变体一律走真实接口。此处的 USE_FAKE 已置 false。
            // 🚫 2026-10-17：fake 也不再内置 10.0.2.2 占位——模拟器/真机联调同样在登录页手输站点。
            buildConfigField("boolean", "USE_FAKE", "false")
        }
        create("prod") {
            dimension = "mode"
            buildConfigField("boolean", "USE_FAKE", "false")
            // 🚫 无构建期默认地址：首启由登录页强制「接入站点」。
        }
    }

    buildTypes {
        debug {
            // debug 不做证书锁定，方便用 Charles/mitmproxy 调试
            isMinifyEnabled = false
        }
        release {
            // ✅ P2-13：开启 R8 + 资源压缩（原先全关，APK 可被直接反编译）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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

    // 🔒 P0-3（2026-10-08）：加密存储。密钥存 Android Keystore，不出系统。
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}