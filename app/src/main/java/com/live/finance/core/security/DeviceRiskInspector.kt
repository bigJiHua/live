package com.live.finance.core.security

import android.os.Build
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 设备环境风险检测（安全整改 P2-14，2026-10-08）
 *
 * ## 定位：**纯信号，不拒服务**
 * 检测结果**只用于上报** `POST /security/risk-report` 落审计日志
 * （`security_verify_log.action_type='risk_signal'`），本端**不拦截、不弹窗、不退出**。
 * 是否处置由部署者看日志自行决定 —— 这是对齐 Plan.md D 系列决策的刻意克制：
 * 误杀（把正常用户的root机拒之门外）比漏报代价大得多。
 *
 * ## 检测强度说明（都是"尽力而为"的静态信号）
 * - 全部不依赖第三方库；每项都可能被 root/隐藏工具绕过（Magisk DenyList、
 *   Frida 改端口等），**信号缺失 ≠ 环境干净**，别拿它做准入判断。
 * - 信号名是 API 端白名单（`riskReport` 的 SIGNAL_NAME_RE：小写字母数字下划线冒号连字符），
 *   新增信号两端同步。
 */
object DeviceRiskInspector {

    /** 全部检测（耗时项控制在毫秒级，登录后后台调用一次） */
    fun detect(): List<String> {
        val signals = mutableListOf<String>()
        try {
            if (android.os.Debug.isDebuggerConnected()) signals += "debugger"
            signals += detectRoot()
            if (isEmulator()) signals += "emulator"
            if (hasXposed()) signals += "xposed"
            if (hasFrida()) signals += "frida"
        } catch (_: Exception) {
            // 检测自身异常绝不能影响登录流程
        }
        return signals
    }

    /** root 特征：常见提权二进制 / 管理器包 / Magisk 目录的存在性 */
    private fun detectRoot(): List<String> {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/data/adb/magisk", "/data/adb/ksu",
        )
        if (suPaths.any { File(it).exists() }) return listOf("root")

        // 注：Magisk DenyList 可隐藏上述路径 ⇒ root 信号缺失 ≠ 环境干净（见类注释）。
        // 管理器包名扫描需要 Context + PackageManager，登录流程里拿不到，
        // 保持零依赖的静态检测（对齐「不过度设计」）。
        return emptyList()
    }

    /** 模拟器特征：Build 指纹关键字 */
    private fun isEmulator(): Boolean {
        val markers = listOf(
            "generic", "sdk_gphone", "emulator", "goldfish", "ranchu",
            "vbox86", "nox", "bluestacks", "bignox",
        )
        val fp = "${Build.FINGERPRINT} ${Build.PRODUCT} ${Build.MODEL} ${Build.HARDWARE}".lowercase()
        return markers.any { fp.contains(it) }
    }

    /** Xposed / LSPosed：classpath 探测（hook 框架注入后类加载必成功） */
    private fun hasXposed(): Boolean = runCatching {
        Class.forName("de.robv.android.xposed.XposedBridge")
        true
    }.getOrDefault(false) || runCatching {
        Class.forName("de.robv.android.xposed.XposedHelpers")
        true
    }.getOrDefault(false)

    /** Frida：默认端口 27042 探测（改端口可绕过，弱信号） */
    private fun hasFrida(): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", 27042), 150)
        }
        true
    }.getOrDefault(false)
}
