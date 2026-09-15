package com.live.finance.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val auth = App.of(LocalContext.current).graph.auth
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf(if (com.live.finance.core.AppConfig.useFake) "demo@example.com" else "") }
    var pwd by remember { mutableStateOf(if (com.live.finance.core.AppConfig.useFake) "123456" else "") }
    var loading by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .padding(24.dp),
    ) {
        Column {
            Spacer(Modifier.height(80.dp))
            androidx.compose.foundation.text.BasicText(
                "财管",
                style = TextStyle(color = colors.primary, fontSize = 32.sp),
            )
            Spacer(Modifier.height(32.dp))
            LabeledField("邮箱", email, { email = it }, KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(12.dp))
            LabeledField(
                "密码", pwd, { pwd = it },
                KeyboardOptions(keyboardType = KeyboardType.Password),
                password = true,
            )
            Spacer(Modifier.height(28.dp))
            VanButton(
                text = "登录",
                onClick = {
                    scope.launch {
                        loading = true
                        when (val r = auth.login(email, pwd)) {
                            is ApiResult.Ok -> nav.navigate(Routes.MAIN) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                            is ApiResult.Fail -> toast.show(r.message)
                            ApiResult.Unauthorized -> toast.show("账号或密码错误")
                            else -> toast.show("登录失败")
                        }
                        loading = false
                    }
                },
                type = VanButtonType.Primary,
                loading = loading,
                block = true,
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    password: Boolean = false,
) {
    val colors = LocalAppColors.current
    Column {
        androidx.compose.foundation.text.BasicText(
            label, style = TextStyle(color = colors.textSecondary, fontSize = 13.sp)
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgCard)
                .padding(12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        "请输入", style = TextStyle(color = colors.textPlaceholder, fontSize = 16.sp)
                    )
                }
                inner()
            },
        )
    }
}
