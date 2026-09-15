package com.live.finance.ui.user

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.ui.common.AppCard
import com.live.finance.ui.common.MenuRow
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.common.SectionText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import kotlinx.coroutines.launch

@Composable
fun UserScreen(nav: NavHostController) {
    val context = LocalContext.current
    val toast = LocalVanToastController.current
    val auth = App.of(context).graph.auth
    val scope = rememberCoroutineScope()
    var user by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.live.finance.data.model.User?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        (auth.getUserinfo() as? com.live.finance.core.net.ApiResult.Ok)?.let { user = it.data }
    }

    ScreenScaffold { inner ->
        androidx.compose.foundation.lazy.LazyColumn(modifier = inner) {
            item {
                AppCard(Modifier.padding(top = 16.dp)) {
                    SectionText(user?.displayName ?: "我的", large = true)
                    MenuRow(title = "邮箱", value = user?.email ?: "")
                    MenuRow(title = "编辑资料", onClick = { nav.navigate(com.live.finance.core.nav.Routes.PROFILE_EDIT) })
                    MenuRow(title = "分类管理", onClick = { nav.navigate(com.live.finance.core.nav.Routes.CATEGORY_MANAGE) })
                    MenuRow(title = "文件资源", onClick = { nav.navigate(com.live.finance.core.nav.Routes.RESOURCE_LIST) })
                    MenuRow(title = "PIN 码", onClick = { nav.navigate(com.live.finance.core.nav.Routes.PIN_SETUP) })
                    MenuRow(title = "应用设置", onClick = { nav.navigate(com.live.finance.core.nav.Routes.APP_SETTINGS) })
                }
                VanButton(
                    text = "退出登录",
                    onClick = {
                        scope.launch {
                            auth.logout()
                            toast.show("已退出")
                            nav.navigate(Routes.LOGIN) {
                                popUpTo(Routes.MAIN) { inclusive = true }
                            }
                        }
                    },
                    type = VanButtonType.Danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        }
    }
}
