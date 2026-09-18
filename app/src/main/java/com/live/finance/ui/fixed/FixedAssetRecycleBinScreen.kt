package com.live.finance.ui.fixed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FixedAsset
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanEmpty
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanConfirmDialog
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch

/**
 * 固定资产回收站 —— 一比一复刻 web `views/Finance/fixedAsset/components/RecycleBin.vue`
 * （页面壳 `RecycleBin.vue` 只是薄包装）。
 *
 * 结构：`.recycle-bin{padding 12/16}` → 顶部计数条（`N 个已删除资产`，居中 14px tertiary）→
 * 资产卡列表（60 图 + 名称/品类/购买价 + 右侧竖排「恢复 / 永久删除」两键）→ 空态（`van-empty 回收站为空`）/ 加载态；
 * 恢复与永久删除各带二次确认（标题与文案照抄 web，永久删除标题为危险色）。
 */
@Composable
fun FixedAssetRecycleBinScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var list by remember { mutableStateOf<List<FixedAsset>>(emptyList()) }
    var restoreTarget by remember { mutableStateOf<FixedAsset?>(null) }
    var deleteTarget by remember { mutableStateOf<FixedAsset?>(null) }

    suspend fun loadList() {
        loading = true
        list = when (val r = graph.fixed.recycleBin()) {
            is ApiResult.Ok -> r.data.orEmpty()
            else -> emptyList()
        }
        loading = false
    }

    LaunchedEffect(Unit) { loadList() }

    ScreenScaffold { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.bgPage)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // .stats-bar{bg secondary; radius 8; padding 12/16; mb 12; center; 14px tertiary}
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                FText("${list.size} 个已删除资产", 14f, FontWeight.Normal, colors.textTertiary)
            }
            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    VanLoading(vertical = true, text = "加载中...")
                }

                list.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    VanEmpty(description = "回收站为空")
                }

                else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    list.forEach { item ->
                        RecycleCard(
                            item = item,
                            onRestore = { restoreTarget = item },
                            onDelete = { deleteTarget = item },
                        )
                    }
                }
            }
        }
    }

    // 恢复确认（web：确认恢复 / 确定要恢复这个资产吗？）
    VanConfirmDialog(
        show = restoreTarget != null,
        title = "确认恢复",
        message = "确定要恢复这个资产吗？",
        onConfirm = {
            val target = restoreTarget
            restoreTarget = null
            if (target != null) {
                scope.launch {
                    when (graph.fixed.restore(target.id)) {
                        is ApiResult.Ok -> {
                            toast.success("恢复成功")
                            loadList()
                        }
                        else -> toast.show("恢复失败")
                    }
                }
            }
        },
        onCancel = { restoreTarget = null },
        onClose = { restoreTarget = null },
    )

    // 永久删除确认（web：警告 / 永久删除后数据无法恢复，确定要删除吗？）
    VanConfirmDialog(
        show = deleteTarget != null,
        title = "警告",
        message = "永久删除后数据无法恢复，确定要删除吗？",
        onConfirm = {
            val target = deleteTarget
            deleteTarget = null
            if (target != null) {
                scope.launch {
                    when (graph.fixed.permanentDelete(target.id)) {
                        is ApiResult.Ok -> {
                            toast.success("删除成功")
                            loadList()
                        }
                        else -> toast.show("删除失败")
                    }
                }
            }
        },
        onCancel = { deleteTarget = null },
        onClose = { deleteTarget = null },
    )
}

/** 回收站卡片（web `.asset-card`：60 图 + 内容 + 右侧竖排两键，gap 12、圆角 12、padding 12）。 */
@Composable
private fun RecycleCard(item: FixedAsset, onRestore: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (item.imgUrl.isNotEmpty()) {
                VanImage(
                    src = AppConfig.fullFileUrl(item.imgUrl),
                    modifier = Modifier.fillMaxSize(),
                    fit = ContentScale.Crop,
                    showError = false,
                )
            } else {
                Box(Modifier.fillMaxSize().background(colors.bgPage), contentAlignment = Alignment.Center) {
                    VanIcon(name = "photo-o", size = 20.sp, color = colors.textTertiary)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                item.info,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            if (item.tag.isNotEmpty()) {
                FText(item.tag, 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(top = 2.dp))
            }
            FText("购买价 ¥${fixed2(item.buyPrice)}", 12f, FontWeight.Normal, colors.textSecondary, Modifier.padding(top = 4.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                text = "恢复",
                onClick = onRestore,
                type = AppButtonType.Primary,
                size = AppButtonSize.Small,
                plain = true,
            )
            AppButton(
                text = "永久删除",
                onClick = onDelete,
                type = AppButtonType.Danger,
                size = AppButtonSize.Small,
                plain = true,
            )
        }
    }
}
