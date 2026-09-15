package com.live.finance.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanField
import kotlinx.coroutines.launch

@Composable
fun ProfileEditScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val auth = App.of(ctx).graph.auth
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }  // 新的相对路径；null=未改
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var uploadingAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (auth.getUserinfo() as? ApiResult.Ok)?.data?.let { username = it.username; email = it.email }
        loading = false
    }

    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            uploadingAvatar = true
            scope.launch {
                val picked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.live.finance.ui.common.compressUriToJpeg(ctx, uri, maxSide = 512)
                }
                if (picked == null) { toast.show("读取图片失败"); uploadingAvatar = false; return@launch }
                val r = App.of(ctx).graph.client.uploadRaw("/upload/single", "file", picked.fileName, picked.mime, picked.bytes, mapOf("busType" to "other"))
                uploadingAvatar = false
                when (r) {
                    is ApiResult.Ok -> {
                        val path = r.data?.get("file_path")?.takeIf { !it.isJsonNull }?.asString
                            ?: r.data?.get("url")?.takeIf { !it.isJsonNull }?.asString
                        if (path != null) { avatar = path; toast.success("头像已上传") } else toast.show("上传返回空")
                    }
                    is ApiResult.Fail -> toast.show(r.message)
                    else -> toast.show("上传失败")
                }
            }
        }
    }

    ScreenScaffold { mod ->
        Column(mod.padding(16.dp)) {
            FText("编辑资料", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.live.vant.basic.VanImage(
                    src = avatar?.let { com.live.finance.core.AppConfig.fullFileUrl(it) } ?: "",
                    modifier = Modifier.size(56.dp), width = 56.dp, height = 56.dp, radius = 28.dp,
                )
                Spacer(Modifier.width(12.dp))
                VanButton(text = if (uploadingAvatar) "上传中…" else "更换头像", type = VanButtonType.Default, loading = uploadingAvatar,
                    onClick = { avatarPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            }
            Spacer(Modifier.height(16.dp))
            VanField(value = username, onValueChange = { username = it }, label = "昵称", placeholder = "2-10 位")
            VanField(value = email, onValueChange = {}, label = "邮箱", readonly = true)
            Spacer(Modifier.height(24.dp))
            VanButton(
                text = "保存", type = VanButtonType.Primary, block = true, loading = saving,
                onClick = {
                    if (username.length < 2 || username.length > 10) { toast.show("昵称需 2-10 位"); return@VanButton }
                    saving = true
                    scope.launch {
                        when (val r = auth.updateProfile(username, avatar)) {
                            is ApiResult.Ok -> { toast.success("已保存"); nav.popBackStack() }
                            is ApiResult.Fail -> toast.show(r.message)
                            else -> toast.show("保存失败")
                        }
                        saving = false
                    }
                },
            )
        }
    }
}
