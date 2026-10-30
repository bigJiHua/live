package com.live.finance.core.glass

/**
 * 底部悬浮岛的玻璃参数（2026-10-19）。
 *
 * **单一事实来源**：
 *  实验室调参 → [com.live.finance.di.AppGraph.setBarGlass] 实时更新（不落盘，立即生效）
 *  → `MainScreen` 读 `graph.barGlass.value` 渲染；
 *  「保存设定」→ [com.live.finance.di.AppGraph.persistBarGlass] 写 [com.live.finance.core.store.GlassStore]（重启仍生效）。
 *
 * 默认值 = 负责人 2026-10-19 在实验室选定的那组（原 B 预设）：
 * 浅α 0.20 / 深α 0.08 / 模糊 2dp / 饱和 1.09 / 受光 0 / 镜面 0 /
 * 胶囊 0.65·0.02。
 */
data class BarGlassSpec(
    /** 白色 tint 不透明度（浅色模式）。 */
    val alphaLight: Float = 0.20f,
    /** 白色 tint 不透明度（深色模式）。 */
    val alphaDark: Float = 0.08f,
    /**
     * backdrop 高斯模糊半径（dp）。
     * ⚠ 2026-10-19 负责人「BG 背景还有透视」⇒ 2dp 太清（背后内容几乎原样透出），
     *   提到 **20dp**：背景被扩散成柔光，不再读作"透视/透明窗"。
     */
    val blurDp: Float = 20f,
    /** backdrop 提饱和倍数。 */
    val saturation: Float = 1.09f,
    /** 上缘受光白 α（0 = 关闭）。 */
    val highlight: Float = 0f,
    /** 外缘镜面描边白 α（0 = 关闭）。 */
    val specular: Float = 0f,
    /** 选中胶囊白 α（浅色）。 */
    val capsuleLight: Float = 0.65f,
    /** 选中胶囊白 α（深色）。 */
    val capsuleDark: Float = 0.02f,
    // 折射环/胶囊透镜 5 个 rim 参数已删除（2026-10-27）：胶囊透镜 2026-10-19 撤销后
    // 渲染链路再无任何读取点（grep 实证），属「调了没效果」的死参数；新组件的透镜
    // 参数走 [NewBarSpec]。
    /**
     * 底栏是否因页面滚动而收起（2026-10-19 负责人要求做成开关，默认**开**）。
     * 开 = 向下浏览时收起、向上回退时弹起；关 = 始终固定显示。
     */
    val collapseOnScroll: Boolean = true,
) {
    /** 夹到合法区间：手改/旧数据/异常输入一律收口，渲染侧不再重复判断。 */
    fun clamped(): BarGlassSpec = copy(
        alphaLight = alphaLight.coerceIn(0f, 1f),
        alphaDark = alphaDark.coerceIn(0f, 1f),
        blurDp = blurDp.coerceIn(0f, 40f),
        saturation = saturation.coerceIn(1f, 2f),
        highlight = highlight.coerceIn(0f, 0.3f),
        specular = specular.coerceIn(0f, 0.85f),
        capsuleLight = capsuleLight.coerceIn(0f, 0.95f),
        capsuleDark = capsuleDark.coerceIn(0f, 0.6f),
    )
}
