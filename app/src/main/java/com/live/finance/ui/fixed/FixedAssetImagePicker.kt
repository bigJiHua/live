package com.live.finance.ui.fixed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.Resource
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.compressUriToJpeg
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 资产图片的资源库 busType（web `getAssetImageList` / `uploadAssetImage` 均用 `product`）。 */
internal const val BUS_TYPE_PRODUCT = "product"

/**
 * 资产图片选择弹窗 —— 一比一复刻 web `AssetForm.vue` / `Edit.vue` 里那段
 * `app-popup(bottom round, height 85%)` + `.image-picker-popup`：
 * 标题 `选择资产图片` + `cross` → 「上传新图片」（80×80 虚线触发框，选完即传 `/upload/multiple`，`busType=product`、
 * `remark=logo资产`）→ `van-divider 或选择已有图片` → 4 列九宫格（选中态 = 2px 主色描边 + 右上角 20px 主色圆勾）→
 * 加载态 / `暂无图片` 空态。
 *
 * @param current 当前已选图片路径（用于高亮选中项）
 * @param onPick 选中回调（回传 `url || file_path`）
 */
@Composable
internal fun FixedAssetImagePickerPopup(
    show: Boolean,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var imageList by remember { mutableStateOf<List<Resource>>(emptyList()) }
    var imageLoading by remember { mutableStateOf(false) }
    var imageUploading by remember { mutableStateOf(false) }

    suspend fun loadImageList() {
        imageLoading = true
        imageList = when (val r = graph.resource.list(BUS_TYPE_PRODUCT, "", 100, 0)) {
            is ApiResult.Ok -> r.data.orEmpty()
            else -> {
                toast.show("加载图片失败")
                emptyList()
            }
        }
        imageLoading = false
    }

    LaunchedEffect(show) { if (show) loadImageList() }

    VanPopup(
        show = true,
        onDismissRequest = onDismiss,
        round = true,
        modifier = Modifier.height(LocalConfiguration.current.screenHeightDp.dp * 0.85f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FText("选择资产图片", 17f, FontWeight.SemiBold, colors.textPrimary)
                Box(Modifier.clickable(onClick = onDismiss).padding(4.dp)) {
                    VanIcon(name = "cross", size = 18.sp, color = colors.textTertiary)
                }
            }

            FText("上传新图片", 14f, FontWeight.Normal, colors.textSecondary)
            Spacer(Modifier.height(8.dp))

            val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri == null || imageUploading) return@rememberLauncherForActivityResult
                imageUploading = true
                scope.launch {
                    toast.show("上传中...")
                    val f = withContext(Dispatchers.IO) { compressUriToJpeg(ctx, uri, maxSide = 1920) }
                    if (f == null) {
                        imageUploading = false
                        toast.show("上传失败")
                        return@launch
                    }
                    val r = graph.client.uploadRaw(
                        "/upload/multiple", "files", f.fileName, f.mime, f.bytes,
                        mapOf("busType" to BUS_TYPE_PRODUCT, "remark" to "logo资产"),
                    )
                    imageUploading = false
                    when (r) {
                        is ApiResult.Ok -> {
                            toast.success("上传成功")
                            loadImageList()
                        }
                        else -> toast.show("上传失败")
                    }
                }
            }
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .clickable(enabled = !imageUploading) {
                        uploadPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (imageUploading) {
                    VanLoading(size = 22.sp, color = colors.textTertiary)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VanIcon(name = "plus", size = 24.sp, color = colors.textTertiary)
                        Spacer(Modifier.height(4.dp))
                        FText("选择文件", 12f, FontWeight.Normal, colors.textTertiary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // van-divider：或选择已有图片
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(colors.border))
                FText("或选择已有图片", 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(horizontal = 12.dp))
                Box(Modifier.weight(1f).height(1.dp).background(colors.border))
            }
            Spacer(Modifier.height(12.dp))

            when {
                imageLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    VanLoading(size = 24.sp, text = "加载中...")
                }

                imageList.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    FText("暂无图片", 14f, FontWeight.Normal, colors.textSecondary)
                }

                else -> Column(Modifier.fillMaxWidth()) {
                    imageList.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { item ->
                                val selected = current == item.filePath
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(2.dp, if (selected) colors.primary else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable { onPick(item.filePath) },
                                ) {
                                    VanImage(
                                        src = AppConfig.fullFileUrl(item.thumbPath),
                                        modifier = Modifier.fillMaxSize(),
                                        fit = ContentScale.Crop,
                                        showError = false,
                                    )
                                    if (selected) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 4.dp, end = 4.dp)
                                                .size(20.dp)
                                                .background(colors.primary, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { VanIcon(name = "success", size = 12.sp, color = Color.White) }
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
