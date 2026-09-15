package com.live.finance.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanField
import com.live.vant.form.VanFieldType
import kotlinx.coroutines.launch

@Composable
fun PinSetupScreen(nav: NavHostController, mode: String = "set") {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val sec = App.of(ctx).graph.security
    val scope = rememberCoroutineScope()
    val isChange = mode == "change"

    var oldPin by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun valid(p: String) = p.length == 6 && p.all { it.isDigit() } && !p.all { it == p[0] }

    ScreenScaffold { mod ->
        Column(mod.padding(16.dp)) {
            FText(if (isChange) "修改 PIN 码" else "设置 PIN 码", 18f, FontWeight.Bold, colors.textPrimary, Modifier.padding(bottom = 16.dp))
            FText("6 位数字，不能全部相同", 12f, FontWeight.Normal, colors.textTertiary, Modifier.padding(bottom = 12.dp))
            if (isChange) VanField(value = oldPin, onValueChange = { oldPin = it.filter { c -> c.isDigit() }.take(6) }, label = "旧PIN", type = VanFieldType.Password, maxlength = 6)
            VanField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = if (isChange) "新PIN" else "PIN", type = VanFieldType.Password, maxlength = 6)
            VanField(value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) }, label = "确认", type = VanFieldType.Password, maxlength = 6)
            Spacer(Modifier.height(24.dp))
            VanButton(
                text = "保存", type = VanButtonType.Primary, block = true, loading = saving,
                onClick = {
                    when {
                        isChange && !valid(oldPin) -> toast.show("旧 PIN 需 6 位数字")
                        !valid(pin) -> toast.show("PIN 需 6 位数字且不全相同")
                        pin != confirm -> toast.show("两次输入不一致")
                        else -> {
                            saving = true
                            scope.launch {
                                val r = if (isChange) sec.changePin(oldPin, pin) else sec.setPin(pin)
                                saving = false
                                when (r) {
                                    is ApiResult.Ok -> { toast.success(r.message.ifBlank { "已保存" }); nav.popBackStack() }
                                    is ApiResult.Fail -> toast.show(r.message)
                                    else -> toast.show("操作失败")
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
