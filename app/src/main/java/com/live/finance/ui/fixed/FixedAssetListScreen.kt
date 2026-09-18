package com.live.finance.ui.fixed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FixedAsset
import com.live.finance.theme.LocalAppColors
import com.live.finance.theme.LocalAppTokens
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.basic.VanTagType
import com.live.vant.feedback.VanPullRefresh
import com.live.vant.feedback.rememberVanPullRefreshState
import com.live.vant.icon.VanIcon
import com.live.vant.nav.VanTabItem
import com.live.vant.nav.VanTabs
import kotlinx.coroutines.launch

/**
 * 固定资产列表 —— 一比一复刻 web `views/Finance/fixedAsset/List.vue`。
 *
 * 结构：渐变态统计卡（资产总数 / 总购买价 / 当前总价值）→ 四页签筛选（全部 · 使用中 · 折旧完毕 · 已归档）→
 * 资产卡列表（70 图 + 名称/状态标签 + 品类 + 购买价/当前价值 + 月折旧·累计折旧 + 已使用·每日成本·折旧完毕）→
 * 底部固定两键（登记固定资产 / 回收站）→ 登记·编辑表单弹窗。
 *
 * ⚠ 与 web 的两处「照抄实际渲染」：
 *  1. 金额用**本域**的 [fixed2]（web `(v).toFixed(2)`），**无千分位**，不是 `Money.format`；
 *  2. 空状态 web 写的是 `<van-icon name="albums-o" size="60">`，但 **Vant 4.9.22 图标字体里没有 `albums-o`**
 *     （`al*` 只有 `alipay`）→ 实际渲染就是**一块 160px 空图位 + 描述**，原生按此复刻（不是随手换图标）。
 */
@Composable
fun FixedAssetListScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<FixedAsset>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf("all") }
    var showForm by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val refreshState = rememberVanPullRefreshState()

    suspend fun load() {
        loading = true
        rows = when (val r = graph.fixed.list()) {
            is ApiResult.Ok -> r.data.orEmpty()
            else -> emptyList()
        }
        loading = false
    }

    LaunchedEffect(reloadKey) { load() }

    // web `displayList`：全部 / status==using / deprec_finished===1 / 已归档（报废·出售·遗失）
    val displayList = remember(rows, activeTab) {
        when (activeTab) {
            "using" -> rows.filter { it.status == "using" }
            "finished" -> rows.filter { it.deprecFinished }
            "archived" -> rows.filter { it.archived }
            else -> rows
        }
    }
    val totalBuyPrice = remember(rows) { rows.sumOf { it.buyPrice } }
    val totalCurrentValue = remember(rows) { rows.sumOf { it.currentValue } }

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize()) {
            VanPullRefresh(
                state = refreshState,
                isChildAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
                onRefresh = {
                    scope.launch {
                        load()
                        refreshState.finishRefresh()
                    }
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(colors.bgPage),
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    // ⚠ token 名是 `grad`（= preset.grad，web `--theme-primary-grad`），不是 `primaryGrad`
                    item { StatsCard(rows.size, totalBuyPrice, totalCurrentValue, tokens.primary, tokens.grad) }
                    item {
                        // .filter-tabs{margin 0 16; bg secondary; radius 8}
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(colors.bgCard)) {
                            VanTabs(
                                active = activeTab,
                                onActiveChange = { activeTab = it },
                                tabs = listOf(
                                    VanTabItem("all", "全部"),
                                    VanTabItem("using", "使用中"),
                                    VanTabItem("finished", "折旧完毕"),
                                    VanTabItem("archived", "已归档"),
                                ),
                                content = {},
                            )
                        }
                    }

                    if (loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                VanLoading(vertical = true, text = "加载中...")
                            }
                        }
                    } else if (displayList.isEmpty()) {
                        item { FixedEmpty(colors.textSecondary) }
                    } else {
                        items(displayList, key = { it.id }) { a ->
                            AssetCard(a) { nav.navigate(Routes.fixedAssetDetail(a.id)) }
                        }
                    }
                }
            }

            // 底部固定两键（web `.bottom-actions{position:fixed; bottom:20; left/right:16; gap:12}`）
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppButton(
                    text = "登记固定资产",
                    onClick = { showForm = true },
                    type = AppButtonType.Primary,
                    round = true,
                    block = true,
                    icon = "plus",
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "回收站",
                    onClick = { nav.navigate(Routes.FIXED_ASSET_RECYCLE) },
                    round = true,
                    block = true,
                    icon = "delete-o",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 登记表单（列表页只用于新增：web `openAddForm` 把 editingAsset 置 null）
    FixedAssetFormPopup(
        show = showForm,
        asset = null,
        onDismiss = { showForm = false },
        onSuccess = { reloadKey++ },
    )
}

/** web `.stats-card`：135° 主色→主色渐变终点，白字三列（总数 / 总购买价 / 当前总价值）。 */
@Composable
private fun StatsCard(
    count: Int,
    totalBuy: Double,
    totalCurrent: Double,
    primary: Color,
    primaryGrad: Color,
) {
    val grad = Brush.linearGradient(listOf(primary, primaryGrad))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(grad)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell("资产总数", "$count", Modifier.weight(1f), valueAlpha = 1f)
        Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.3f)))
        StatCell("总购买价", "¥${fixed2(totalBuy)}", Modifier.weight(1f), valueAlpha = 1f)
        Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.3f)))
        StatCell("当前总价值", "¥${fixed2(totalCurrent)}", Modifier.weight(1f), valueAlpha = 0.95f)
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, valueAlpha: Float) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, FontWeight.Normal, Color.White.copy(alpha = 0.85f))
        FText(value, 16f, FontWeight.Bold, Color.White.copy(alpha = valueAlpha))
    }
}

/** 资产卡（web `.asset-card`：白卡 radius 12 / padding 12 / mb 12）。 */
@Composable
private fun AssetCard(a: FixedAsset, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tokens = LocalAppTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // .card-image 70×70（无图 → bg-primary 底 + photo-o 24）
        Box(Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (a.imgUrl.isNotEmpty()) {
                VanImage(
                    src = AppConfig.fullFileUrl(a.imgUrl),
                    modifier = Modifier.fillMaxSize(),
                    fit = ContentScale.Crop,
                    showError = false,
                )
            } else {
                Box(Modifier.fillMaxSize().background(colors.bgPage), contentAlignment = Alignment.Center) {
                    VanIcon(name = "photo-o", size = 24.sp, color = colors.textTertiary)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // .card-header：名称 15/600 + 状态标签
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // `.card-title{font-size:15px; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap}`
                BasicText(
                    a.info,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                VanTag(text = a.statusLabel, type = a.statusTagType.toVanTagType(), size = VanTagSize.Small)
            }
            if (a.tag.isNotEmpty()) {
                FText(a.tag, 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 4.dp))
            }
            // .card-info{gap 16; margin-top 8}：购买价 / 当前价值（当前价值用 danger）
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoRow("购买价", "¥${fixed2(a.buyPrice)}", colors.textPrimary)
                InfoRow("当前价值", "¥${fixed2(a.currentValue)}", colors.danger)
            }
            // .card-footer{margin-top 6; gap 8}：折旧两行 + 已使用 / 每日成本 / 折旧完毕
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (a.monthDeprec > 0) FText("月折旧 ¥${fixed2(a.monthDeprec)}", 11f, FontWeight.Normal, colors.textTertiary)
                    if (a.totalDeprec > 0) FText("累计折旧 ¥${fixed2(a.totalDeprec)}", 11f, FontWeight.Normal, colors.textTertiary)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FText("已使用：${usedTimeLabel(a)}", 11f, FontWeight.Normal, tokens.primary)
                    FText("每日成本 ¥${dailyCost(a)}", 11f, FontWeight.Normal, colors.danger)
                    if (a.deprecFinished) {
                        VanTag(text = "折旧完毕", type = VanTagType.Success, size = VanTagSize.Small, plain = true)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        VanIcon(name = "arrow", size = 14.sp, color = colors.textTertiary)
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, FontWeight.Normal, colors.textTertiary)
        FText(value, 13f, FontWeight.SemiBold, valueColor)
    }
}

/**
 * 空状态：**照抄 web 实际渲染**（见类注释第 2 条）—— 160px 空图位 + 描述。
 * Vant `.van-empty__description{margin-top:16px; font-size:14px; color:text-secondary}`。
 */
@Composable
private fun FixedEmpty(descriptionColor: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(160.dp))
        Spacer(Modifier.height(16.dp))
        FText("暂无固定资产", 14f, FontWeight.Normal, descriptionColor)
    }
}

/** web `getStatusType` → `app-tag` 的 type（原生用 [com.live.vant.basic.VanTag]）。 */
