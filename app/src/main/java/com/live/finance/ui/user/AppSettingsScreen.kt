package com.live.finance.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.ThemeMode
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.SectionText
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val mode = graph.themeMode.value

    ScreenScaffold { _ ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionText("外观")
            Spacer(Modifier.height(8.dp))
            ThemeMode.values().forEach { m ->
                OptionRow(label = m.label(), selected = m == mode, onClick = {
                    CoroutineScope(Dispatchers.Main).launch { graph.setThemeMode(m) }
                })
            }
            Spacer(Modifier.height(24.dp))
            SectionText("账号")
            Spacer(Modifier.height(8.dp))
            VanButton(
                text = "退出登录", type = VanButtonType.Danger, block = true,
                onClick = {
                    scope.launch {
                        graph.auth.logout()
                        toast.show("已退出")
                        nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                    }
                },
            )
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.bgCard).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 15f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
        if (selected) VanIcon(name = "success", size = 20.sp, color = colors.primary)
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.System -> "跟随系统"; ThemeMode.Light -> "浅色"; ThemeMode.Dark -> "深色"
}
