package com.live.finance.ui.diary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.compressUriToJpeg
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.basic.VanImage
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanField
import com.live.vant.form.VanFieldType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MOODS = listOf("😊", "🙂", "😐", "😢", "😡", "🎉")

@Composable
fun DiaryAddScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("😊") }
    var imageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val picked = withContext(Dispatchers.IO) { compressUriToJpeg(ctx, uri) }
                if (picked == null) { toast.show("图片读取失败"); uploading = false; return@launch }
                val r = graph.client.upload(picked.bytes, picked.fileName, picked.mime, "post", "", "")
                uploading = false
                when (r) {
                    is ApiResult.Ok -> if (r.data != null) imageUrls = imageUrls + r.data else toast.show("上传返回空")
                    is ApiResult.Fail -> toast.show(r.message)
                    else -> toast.show("上传失败")
                }
            }
        }
    }

    ScreenScaffold { mod ->
        Column(mod.verticalScroll(rememberScrollState()).padding(16.dp)) {
            FText("发布动态", 18f, FontWeight.Bold, colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().background(colors.bgCard).padding(8.dp)) {
                VanField(value = content, onValueChange = { content = it }, placeholder = "此刻的想法…", type = VanFieldType.Textarea, rows = 5)
            }
            Spacer(Modifier.height(12.dp))
            // 已选图片缩略图
            if (imageUrls.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    imageUrls.forEach { u ->
                        VanImage(src = AppConfig.fullFileUrl(u), modifier = Modifier.size(72.dp), width = 72.dp, height = 72.dp, radius = 8.dp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            VanButton(
                text = if (uploading) "上传中…" else "＋ 添加图片",
                type = VanButtonType.Default, loading = uploading,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            Spacer(Modifier.height(16.dp))
            FText("心情", 13f, FontWeight.Normal, colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MOODS.forEach { m ->
                    val sel = m == mood
                    Box(
                        Modifier.background(if (sel) colors.primaryLight else colors.bgThird, RoundedCornerShape(8.dp))
                            .clickable { mood = m }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { FText(m, 20f, weight = FontWeight.Normal, color = colors.textPrimary) }
                }
            }
            Spacer(Modifier.height(24.dp))
            VanButton(
                text = "发布", type = VanButtonType.Primary, block = true, loading = saving,
                onClick = {
                    if (content.isBlank() && imageUrls.isEmpty()) { toast.show("说点什么或配张图吧"); return@VanButton }
                    saving = true
                    scope.launch {
                        when (val r = graph.moment.create(content.trim(), mood, imageUrls)) {
                            is ApiResult.Ok -> { toast.success("已发布"); nav.popBackStack() }
                            is ApiResult.Fail -> toast.show(r.message)
                            else -> toast.show("发布失败")
                        }
                        saving = false
                    }
                },
            )
        }
    }
}
