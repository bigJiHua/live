package com.live.finance.core.glass

/**
 * 新版独立底部 Tab（NewLiquidGlassTabBar）的材质/透镜参数（2026-10-27）。
 *
 * 背景：液态玻璃实验室的微调面板只服务经典悬浮岛（BarGlassSpec）；「底部 Tab 组件」
 * 开启时面板对新组件完全无效（负责人 2026-10-27 实测报「调了没效果」）⇒ 本类把
 * NewLiquidGlassTabBar 里写死的材质参数提出来，实验室按当前激活的底栏组件调对应的参数集。
 *
 * 单一事实来源：实验室调参 → AppGraph.setNewBarGlass 实时生效；
 * 「保存设定」→ AppGraph.persistNewBarGlass 落盘（NewBarStore），重启读回。
 */
data class NewBarSpec(
    /** 材质 backdrop 高斯模糊半径（px，Classic 版是 dp；这里沿用真机口径 px）。 */
    val blurPx: Float = 20f,
    /** 染色 α（深色模式，近黑）。 */
    val tintDark: Float = 0.40f,
    /** 染色 α（浅色模式，白）。 */
    val tintLight: Float = 0.22f,
    /** AGSL 折射强度（px，边缘法线位移上限）。 */
    val refraction: Float = 8f,
    /** AGSL 色散（R/B 反向偏移比例 0..1）。 */
    val dispersion: Float = 0.35f,
    /** AGSL 边缘白边（Fresnel）系数 0..1。 */
    val fresnel: Float = 0.35f,
    /** 透镜中心倍率（选中项图标/文字的基础放大）。 */
    val lensCenter: Float = 1.3f,
    /** 透镜左右边缘倍率（滑过邻居 icon/文字的放大镜）。 */
    val lensEdge: Float = 1.7f,
    /** 透镜横向过渡带宽（dp；仅左右生效，上下边不参与变倍——2D SDF 会裁字）。 */
    val lensBandDp: Float = 16f,
    /** 胶囊填充 α（深色模式）。 */
    val capsuleDark: Float = 0.12f,
    /** 胶囊填充 α（浅色模式）。 */
    val capsuleLight: Float = 0.28f,
    /** 外投影 α（0..80）。 */
    val shadowAlpha: Int = 40,
) {
    /** 夹到合法区间：手改/旧数据/异常输入一律收口，渲染侧不再重复判断。 */
    fun clamped(): NewBarSpec = copy(
        blurPx = blurPx.coerceIn(0f, 40f),
        tintDark = tintDark.coerceIn(0f, 0.85f),
        tintLight = tintLight.coerceIn(0f, 0.85f),
        refraction = refraction.coerceIn(0f, 24f),
        dispersion = dispersion.coerceIn(0f, 1f),
        fresnel = fresnel.coerceIn(0f, 1f),
        // 中心倍率上限 1.5：图标+文字列 ≈±43px，×1.5≈±64px 恰好贴胶囊半高 65px 不裁切
        lensCenter = lensCenter.coerceIn(1f, 1.5f),
        lensEdge = lensEdge.coerceIn(1f, 2.2f),
        lensBandDp = lensBandDp.coerceIn(4f, 32f),
        capsuleDark = capsuleDark.coerceIn(0f, 0.8f),
        capsuleLight = capsuleLight.coerceIn(0f, 0.8f),
        shadowAlpha = shadowAlpha.coerceIn(0, 80),
    )
}
