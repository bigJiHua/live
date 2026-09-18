package com.live.finance.ui.data

import com.live.finance.data.model.LoginLog

/** web `_suspicious`：等级 + 明细。 */
internal data class Suspicion(val level: String, val detail: String)

private data class Region(val country: String, val province: String)

/** web `extractRegion` 的内网网段判定（10/8、172.16-31/12、192.168/16、127/8、0/8）。 */
private val PRIVATE_IP = Regex("^(10\\.|172\\.(1[6-9]|2\\d|3[01])|192\\.168|127\\.|0\\.)")

/**
 * web `extractRegion`：**优先** `login_location`（按 `-` 切分，第 1 段=国家、第 2 段=省份），
 * 无位置时按 IP 粗判（内网 → `中国（内网）`，其余 → `未知`）；无 IP → 空。
 */
private fun extractRegion(location: String, ip: String): Region {
    if (location.isNotEmpty() && location != "未知") {
        val parts = location.split("-").map { it.trim() }
        return Region(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
    }
    if (ip.isNotEmpty()) {
        return if (PRIVATE_IP.containsMatchIn(ip)) Region("中国（内网）", "") else Region("未知", "")
    }
    return Region("", "")
}

/**
 * 复刻 web `LoginLog.vue` 的 `markSuspicious`（原样口径，勿"优化"）：
 *
 * 1. 只统计**成功**登录（`status !== 0`）；
 * 2. 设备指纹频率最高者 = 常住设备 `mainFP`；
 * 3. 国家 / 省份频率最高者 = `mainCountry` / `mainProvince`（同频取**先出现**的那个，对齐 JS `>` 比较）；
 * 4. 2 小时窗口内相邻两次成功登录换了省份 → **双方**都标 `rapid`（`p1⇢p2`，已标记的不覆盖）；
 * 5. 逐条：指纹≠常住 → `new-device`；否则国家≠常住国 → `cross-border`；否则省≠常住省 → `abnormal`。
 *
 * ⚠ 现实偏差（照抄）：「新设备」分支依赖 `fingerprint`，而**后端列表查询不返回该列** → 线上永不触发；
 *   因此实际生效的只有 `rapid` / `cross-border` / `abnormal` 三种。
 */
internal fun markSuspicious(list: List<LoginLog>): Map<String, Suspicion> {
    if (list.size < 2) return emptyMap()
    val out = LinkedHashMap<String, Suspicion>()
    val success = list.filter { it.status != 0 }

    // 1. 指纹频率
    val fpCount = LinkedHashMap<String, Int>()
    success.forEach { if (it.fingerprint.isNotEmpty()) fpCount[it.fingerprint] = (fpCount[it.fingerprint] ?: 0) + 1 }
    var mainFP = ""
    var maxFP = 0
    fpCount.forEach { (fp, n) -> if (n > maxFP) { maxFP = n; mainFP = fp } }

    // 2. 地区频率
    val regions = HashMap<String, Region>()
    val countryCount = LinkedHashMap<String, Int>()
    val provinceByCountry = LinkedHashMap<String, Int>()
    success.forEach { l ->
        val r = extractRegion(l.loginLocation, l.loginIp)
        regions[l.id] = r
        if (r.country.isNotEmpty()) countryCount[r.country] = (countryCount[r.country] ?: 0) + 1
        if (r.country.isNotEmpty() && r.province.isNotEmpty()) {
            val key = "${r.country}|${r.province}"
            provinceByCountry[key] = (provinceByCountry[key] ?: 0) + 1
        }
    }
    var mainCountry = ""
    var maxCountry = 0
    countryCount.forEach { (c, n) -> if (n > maxCountry) { maxCountry = n; mainCountry = c } }
    var mainProvince = ""
    if (mainCountry.isNotEmpty()) {
        var maxProv = 0
        provinceByCountry.forEach { (key, n) ->
            val c = key.substringBefore('|')
            val p = key.substringAfter('|')
            if (c == mainCountry && n > maxProv) { maxProv = n; mainProvince = p }
        }
    }

    // 3. 2 小时窗口内跨省
    val threshold = 2L * 60 * 60 * 1000
    val sorted = success.sortedBy { it.loginTime.toLongOrNull() ?: 0L }
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val curr = sorted[i]
        val diff = (curr.loginTime.toLongOrNull() ?: 0L) - (prev.loginTime.toLongOrNull() ?: 0L)
        if (diff > 0 && diff <= threshold) {
            val p1 = regions[prev.id]?.province.orEmpty()
            val p2 = regions[curr.id]?.province.orEmpty()
            if (p1.isNotEmpty() && p2.isNotEmpty() && p1 != p2) {
                if (out[prev.id] == null) out[prev.id] = Suspicion("rapid", "$p1⇢$p2")
                if (out[curr.id] == null) out[curr.id] = Suspicion("rapid", "$p1⇢$p2")
            }
        }
    }

    // 4. 逐条标记
    list.forEach { log ->
        if (log.status == 0) return@forEach
        if (mainFP.isNotEmpty() && log.fingerprint.isNotEmpty() && log.fingerprint != mainFP) {
            if (out[log.id] == null) out[log.id] = Suspicion("new-device", "新设备")
            return@forEach
        }
        if (out[log.id] != null) return@forEach
        val r = regions[log.id] ?: return@forEach
        if (r.country.isEmpty()) return@forEach
        if (r.country != mainCountry) {
            out[log.id] = Suspicion("cross-border", r.country)
        } else if (mainProvince.isNotEmpty() && r.province.isNotEmpty() && r.province != mainProvince) {
            out[log.id] = Suspicion("abnormal", r.province)
        }
    }
    return out
}

/** web `suspiciousLabel`（对象形态那套；`new-device` 不带 detail）。 */
internal fun suspicionLabel(s: Suspicion): String = when (s.level) {
    "cross-border" -> "跨境登录(${s.detail})"
    "new-device" -> "新设备登录"
    "rapid" -> "频切异地(${s.detail})"
    "abnormal" -> "异地登录(${s.detail})"
    else -> s.level
}

/** web `suspiciousType`：cross-border/new-device/rapid → danger，其余（abnormal）→ warning。 */
internal fun suspicionTagType(s: Suspicion): String = when (s.level) {
    "cross-border", "new-device", "rapid" -> "danger"
    else -> "warning"
}
