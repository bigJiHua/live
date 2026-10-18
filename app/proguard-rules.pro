# ============================================================
# R8 / ProGuard 规则（安全整改 P2-13，2026-10-08）
#
# release 构建开启了 isMinifyEnabled + isShrinkResources。
# ⚠️ 开启混淆后，反射/Gson/Compose 会因类名被改而崩溃，
#    所以下面必须显式 keep 住这些「靠反射工作」的类。
#    若开了 R8 后出现「类找不到 / JSON 解析为 null / Compose 崩溃」，
#    第一反应就是来这里加 keep 规则。
# ============================================================

# ---- 业务 model：Gson 靠反射读字段，字段名被改就解析失败 ----
-keep class com.live.finance.data.model.** { *; }
-keepclassmembers class com.live.finance.data.model.** { <fields>; }

# ---- 通用包结构（Gson 泛型解析需要保留签名）----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keep class com.google.gson.reflect.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory

# ---- 顶层信封 ----
-keep class com.live.finance.core.net.Envelope { *; }
-keep class com.live.finance.core.net.ApiResult { *; }
-keep class com.live.finance.core.net.ApiResult$* { *; }

# ---- Kotlin 协程 ----
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---- okhttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
# okhttp 引用了这些可选类，实际不会被调用
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ---- 加密库（security-crypto）----
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ---- 枚举：R8 优化会改枚举常量名，序列化时出错 ----
-keepclassmembers enum * { *; }

# ---- Jetpack Compose ----
-dontwarn androidx.compose.**

# ---- 日记富文本 WebView 里注入的 JS 桥 ----
-keepclassmembers class com.live.finance.ui.diary.** {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- 银行卡管理离线 WebView（ui/bankcard/CardWebView.kt）里注入的 JS 桥 ----
# ⚠ 别漏这条：release 开了 R8（build.gradle.kts:134 isMinifyEnabled），漏了会在 release 包里
#   **静默剥掉** 桥方法 —— 表现为「debug 正常、release 里页面一直转圈或点了没反应」。
-keepclassmembers class com.live.finance.ui.bankcard.** {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- 保留行号，便于线上崩溃堆栈还原（关掉会丢失文件名/行号）----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile