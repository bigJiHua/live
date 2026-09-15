package com.live.vant.form

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.basic.VanImage
import com.live.vant.basic.VanLoading
import com.live.vant.icon.VanIcon
import com.live.vant.theme.LocalVantColors
import com.live.vant.theme.LocalVantDimens

/** 上传文件模型（对应 Vant UploaderFileListItem） */
data class VanUploaderFile(
    val id: String,
    /** 本地 Uri 或网络地址（对应 Vant content url） */
    val url: String,
    /** uploading / done / failed（对应 status） */
    val status: String = "done",
    /** 状态角标文字，如 "上传失败" */
    val message: String? = null,
)

/**
 * 复刻 van-uploader（web 项目 8 处使用；props：v-model / max-count / multiple / accept /
 * preview-size / deletable / disabled / upload-text；事件 delete / click-preview）。
 *
 * Vant 的 before-read / after-read（压缩+上传）在原生属于业务逻辑：
 * onPick 回调把选中的 Uri 交给调用方处理（对应 after-read 上传后写回 fileList）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VanUploader(
    fileList: List<VanUploaderFile>,
    onPick: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    maxCount: Int? = null,
    multiple: Boolean = false,
    /** image 开头（默认，系统相册选择器）否则走文档选择器 */
    accept: String = "image/*",
    previewSize: Dp = 80.dp,
    deletable: Boolean = true,
    disabled: Boolean = false,
    uploadText: String? = null,
    onDelete: ((VanUploaderFile, Int) -> Unit)? = null,
    onClickPreview: ((VanUploaderFile, Int) -> Unit)? = null,
) {
    val c = LocalVantColors.current
    val tokens = LocalVantDimens.current

    val pickImages: (List<Uri>) -> Unit = { uris ->
        onPick(if (maxCount != null) uris.take((maxCount - fileList.size).coerceAtLeast(0)) else uris)
    }
    val imagePickerMulti = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris -> pickImages(uris) }
    val imagePickerSingle = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pickImages(listOf(uri)) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> onPick(if (maxCount != null) uris.take((maxCount - fileList.size).coerceAtLeast(0)) else uris) }

    val pick: () -> Unit = {
        if (disabled) Unit
        else if (maxCount != null && fileList.size >= maxCount) Unit
        else if (accept.startsWith("image")) {
            val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (multiple) imagePickerMulti.launch(req) else imagePickerSingle.launch(req)
        } else {
            documentPicker.launch(arrayOf("*/*"))
        }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fileList.forEachIndexed { index, item ->
            Box(
                Modifier
                    .size(previewSize)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .clickable { onClickPreview?.invoke(item, index) },
            ) {
                VanImage(
                    src = item.url,
                    modifier = Modifier.fillMaxSize(),
                    width = previewSize,
                    height = previewSize,
                )
                if (item.status == "uploading") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().background(Color(0xE1323233)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            VanLoading(size = 22.sp, color = Color.White)
                        }
                    }
                } else if (item.status == "failed" && !item.message.isNullOrEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().background(Color(0xE1323233)),
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            item.message,
                            style = TextStyle(color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center),
                        )
                    }
                }
                if (deletable && !disabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp + 8.dp)
                            .clickable { onDelete?.invoke(item, index) },
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(14.dp).background(Color(0xB3000000), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, bottomEnd = 4.dp)),
                        ) {
                            VanIcon(name = "cross", size = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }
        // 上传触发区
        if (maxCount == null || fileList.size < maxCount) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(previewSize)
                    .alpha(if (disabled) 0.5f else 1f)
                    .background(c.bgPage, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .clickable(enabled = !disabled) { pick() },
            ) {
                VanIcon(name = "photograph", size = 24.sp, color = c.buttonDefaultBorder)
                if (!uploadText.isNullOrEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        uploadText,
                        style = TextStyle(color = c.textTertiary, fontSize = tokens.fontSizeSm),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
