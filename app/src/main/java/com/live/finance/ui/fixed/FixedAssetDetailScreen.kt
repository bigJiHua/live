package com.live.finance.ui.fixed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FixedAsset
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppCell
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.basic.VanTag
import com.live.vant.basic.VanTagSize
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import kotlinx.coroutines.launch

/**
 * 固定资产详情 —— 一比一复刻 web `views/Finance/fixedAsset/Detail.vue`。
 *
 * 结构：顶部大图（有图才显示，**250px 高、object-fit cover**）→ 三组 `van-cell-group inset title`：
 * 基本信息（名称 / 品类 / 购买价格 / 购买日期）、折旧信息（年限 / 残值率 / 残值 / 二手市场价 → **分隔线** →
 * 月折旧 / 累计折旧 / 当前账面价值[danger] / 已使用时长 / 上次折旧日期 / 折旧状态标签）、
 * 状态信息（当前状态标签 / 结束日期[有 scrap_date 才显示]）→ 竖排三键（编辑·变更状态·删除，均 plain block）→
 * 状态变更 `van-picker` 弹窗 + 删除二次确认。
 *
 * ⚠ 微差（记档）：web 的删除确认传了 `confirmButtonColor: '#ee0a24'`，原生 [VanConfirmDialog] 无该参数 → 用默认主色。
 */
@Composable
fun FixedAssetDetailScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var asset by remember { mutableStateOf<FixedAsset?>(null) }
    var showStatusPicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    suspend fun loadDetail() {
        loading = true
        asset = (graph.fixed.detail(id) as? ApiResult.Ok)?.data
        loading = false
    }

    LaunchedEffect(id) {
        if (id.isBlank()) {
            toast.show("参数错误")
            nav.popBackStack()
        } else {
            loadDetail()
        }
    }

    ScreenScaffold { inner ->
        Box(inner.fillMaxSize().background(colors.bgPage)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VanLoading(vertical = true, text = "加载中...")
                }

                asset == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FText("资产不存在", 14f, FontWeight.Normal, colors.textTertiary)
                }

                else -> {
                    val a = asset!!
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                        // .detail-image{width:100%; height:250px; bg-secondary}
                        if (a.imgUrl.isNotEmpty()) {
                            Box(Modifier.fillMaxWidth().height(250.dp).background(colors.bgCard)) {
                                VanImage(
                                    src = AppConfig.fullFileUrl(a.imgUrl),
                                    modifier = Modifier.fillMaxSize(),
                                    fit = ContentScale.Crop,
                                    showError = false,
                                )
                            }
                        }

                        DetailGroup("基本信息") {
                            AppCell(title = "资产名称", value = a.info)
                            AppCell(title = "品类", value = a.tag.ifBlank { "-" })
                            AppCell(title = "购买价格", value = "¥${fixed2(a.buyPrice)}")
                            AppCell(title = "购买日期", value = a.buyDate, border = false)
                        }

                        DetailGroup("折旧信息") {
                            AppCell(title = "预计使用年限", value = "${jsNum(a.useYears)}年")
                            AppCell(title = "残值率", value = "${jsNum(a.residualRate)}%")
                            AppCell(title = "残值", value = "¥${fixed2(a.residualVal)}")
                            AppCell(
                                title = "二手市场价",
                                value = if (a.secondhandPrice != 0.0) "¥${fixed2(a.secondhandPrice)}" else "-",
                            )
                            // <van-divider />
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp).height(1.dp).background(colors.border))
                            AppCell(title = "月折旧", value = "¥${fixed2(a.monthDeprec)}")
                            AppCell(title = "累计折旧", value = "¥${fixed2(a.totalDeprec)}")
                            // `.primary-value{color: danger !important; font-weight: 600}`
                            AppCell(title = "当前账面价值", value = "¥${fixed2(a.nowVal)}", valueColor = colors.danger)
                            AppCell(title = "已使用时长", value = usedTimeLabel(a))
                            AppCell(title = "上次折旧日期", value = a.lastDeprecDate.ifBlank { "-" })
                            AppCell(
                                title = "折旧状态",
                                border = false,
                                rightIcon = {
                                    VanTag(
                                        text = if (a.deprecFinished) "已折旧完毕" else "未折旧完",
                                        type = if (a.deprecFinished) com.live.vant.basic.VanTagType.Success else com.live.vant.basic.VanTagType.Primary,
                                        size = VanTagSize.Small,
                                    )
                                },
                            )
                        }

                        DetailGroup("状态信息") {
                            AppCell(
                                title = "当前状态",
                                border = a.scrapDate.isNotEmpty(),
                                rightIcon = {
                                    VanTag(text = a.statusLabel, type = a.statusTagType.toVanTagType())
                                },
                            )
                            if (a.scrapDate.isNotEmpty()) {
                                AppCell(title = "结束日期", value = a.scrapDate, border = false)
                            }
                        }

                        // .action-buttons{flex column; gap 12; padding 16; margin-top 16}
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp).padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppButton(
                                text = "编辑",
                                onClick = { nav.navigate(Routes.fixedAssetEdit(a.id)) },
                                type = AppButtonType.Primary,
                                plain = true,
                                block = true,
                            )
                            AppButton(
                                text = "变更状态",
                                onClick = { showStatusPicker = true },
                                type = AppButtonType.Warning,
                                plain = true,
                                block = true,
                            )
                            AppButton(
                                text = "删除",
                                onClick = { showDelete = true },
                                type = AppButtonType.Danger,
                                plain = true,
                                block = true,
                            )
                        }
                    }
                }
            }
        }
    }

    // 状态变更（van-picker：使用中/已报废/已出售/已遗失）
    VanPopup(show = showStatusPicker, onDismissRequest = { showStatusPicker = false }, round = true) {
        VanPicker(
            columns = listOf(
                listOf(
                    VanPickerOption("使用中", "using"),
                    VanPickerOption("已报废", "scrapped"),
                    VanPickerOption("已出售", "sold"),
                    VanPickerOption("已遗失", "lost"),
                ),
            ),
            title = "选择状态",
            onConfirm = { _, values ->
                val status = values.firstOrNull()?.toString().orEmpty()
                showStatusPicker = false
                if (status.isNotEmpty()) {
                    scope.launch {
                        when (graph.fixed.changeStatus(id, status)) {
                            is ApiResult.Ok -> {
                                toast.success("状态变更成功")
                                loadDetail()
                            }
                            else -> toast.show("变更失败")
                        }
                    }
                }
            },
            onCancel = { showStatusPicker = false },
        )
    }

    // 删除二次确认（web `showConfirmDialog({title:'确认删除', message:'确定要删除这个固定资产吗？'})`）
    VanConfirmDialog(
        show = showDelete,
        title = "确认删除",
        message = "确定要删除这个固定资产吗？",
        onConfirm = {
            showDelete = false
            scope.launch {
                when (graph.fixed.remove(id)) {
                    is ApiResult.Ok -> {
                        toast.success("删除成功")
                        nav.popBackStack()
                    }
                    else -> toast.show("删除失败")
                }
            }
        },
        onCancel = { showDelete = false },
        onClose = { showDelete = false },
    )
}

/**
 * `van-cell-group inset title="xxx"`：标题 14px `text-secondary`（padding `16 16 8`）+
 * 白卡（左右内缩 16、圆角 8、`overflow:hidden`）。
 */
@Composable
private fun DetailGroup(title: String, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth()) {
        FText(
            title, 14f, FontWeight.Normal, colors.textSecondary,
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.bgCard),
        ) { content() }
    }
    Spacer(Modifier.height(0.dp))
}
