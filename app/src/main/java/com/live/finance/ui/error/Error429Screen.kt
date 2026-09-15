package com.live.finance.ui.error

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.core.nav.Routes
import com.live.finance.theme.LocalAppColors
import com.live.vant.basic.VanButton

@Composable
fun Error429Screen(nav: NavHostController) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgPage),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.text.BasicText(
                "请求过于频繁",
                style = TextStyle(color = colors.textSecondary, fontSize = 16.sp),
            )
            VanButton(
                text = "返回首页",
                onClick = { nav.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } } },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
