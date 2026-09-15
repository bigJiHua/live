package com.live.finance.ui.data

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.MenuRow
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import kotlinx.coroutines.launch

@Composable
fun DataManageScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val repo = App.of(ctx).graph.data
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var backups by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) { toast.show("读取文件失败"); importing = false; return@launch }
                when (val r = repo.importSql(bytes, "import.sql")) {
                    is ApiResult.Ok -> toast.success(r.message.ifBlank { "导入成功" })
                    is ApiResult.NeedPin -> toast.show("导入需先验证 PIN")
                    is ApiResult.Fail -> toast.show(r.message)
                    else -> toast.show("导入失败")
                }
                importing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        status = (repo.status() as? ApiResult.Ok)?.data ?: "获取失败"
        backups = (repo.backups() as? ApiResult.Ok)?.data ?: emptyList()
    }

    ScreenScaffold { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            FText("数据管理", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(16.dp))
            Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
                FText("状态", 13f, weight = FontWeight.Normal, color = colors.textSecondary)
                FText(status.take(500), 12f, weight = FontWeight.Normal, color = colors.textTertiary, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            MenuRow(title = "登录日志", onClick = { nav.navigate(Routes.DATA_LOGIN_LOG) })
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                VanButton(text = if (busy) "处理中…" else "导出全库(分享)", type = VanButtonType.Primary, block = true, loading = busy,
                    onClick = {
                        busy = true; scope.launch {
                            when (val r = repo.exportFull()) {
                                is ApiResult.Ok -> shareText(ctx, "数据库导出", r.data ?: "")
                                is ApiResult.Fail -> toast.show(r.message)
                                else -> toast.show("导出失败")
                            }
                            busy = false
                        }
                    })
                Spacer(Modifier.height(8.dp))
                VanButton(text = if (importing) "导入中…" else "SQL 导入(SAF)", type = VanButtonType.Default, block = true, loading = importing,
                    onClick = { importLauncher.launch(arrayOf("*/*")) })
            }
            if (backups.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FText("备份列表", 14f, weight = FontWeight.Medium, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
                backups.forEach { name ->
                    MenuRow(title = name, value = "下载分享", onClick = {
                        scope.launch {
                            when (val r = repo.downloadBackup(name)) {
                                is ApiResult.Ok -> shareText(ctx, name, String(r.data ?: ByteArray(0)))
                                is ApiResult.Fail -> toast.show(r.message)
                                else -> toast.show("下载失败")
                            }
                        }
                    })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun shareText(ctx: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text.ifBlank { "(空)" })
    }
    ctx.startActivity(Intent.createChooser(intent, title))
}
