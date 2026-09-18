package com.live.finance.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.R
import com.live.finance.core.security.RsaUtil
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.vant.basic.VanButton
import com.live.vant.basic.VanButtonType
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.form.VanSecureKeyboard
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 登录页（1:1 复刻 web/src/views/Auth/Login.vue）。
 * 组件位置/输入逻辑/安全键盘对齐：
 *  - 顶部 logo + 「Gold 财管」+ 副标题；账号/密码 **readonly 字段** + 点击唤起安全键盘（非系统键盘）。
 *  - 密码框以 ● 掩码显示，眼睛按钮切换明文（明文时给安全提示），激活字段显示闪烁光标。
 *  - 校验：仅密码长度 6–30 时「立即登录」可用（对齐 web passwordRulesComputed.validLength）。
 *  - 底部：演示模式显示提示，否则显示品牌 footer。
 *  - 安全键盘见 [VanSecureKeyboard]（复刻 FullKeyboard）。
 * 字符上限与后端统一（nameOrEmail ≤50，password 6–30）。
 */
@Composable
fun LoginScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val toast = LocalVanToastController.current
    val graph = App.of(LocalContext.current).graph
    val auth = graph.auth
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(if (AppConfig.useFake) "demo@example.com" else "") }
    var pwd by remember { mutableStateOf(if (AppConfig.useFake) "123456" else "") }
    // 安全键盘密码密文数组（RSA 逐字符加密；非空时提交密文数组，对齐 web secureOnly 协议）
    var pwdCipher by remember { mutableStateOf<List<String>?>(null) }
    // 密码框激活时异步取到的 RSA 公钥（null = 降级为明文，对应 web 公钥未就绪）
    var pwdRsa by remember { mutableStateOf<String?>(null) }
    var reveal by remember { mutableStateOf(false) }
    var activeField by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // 仅密码框且有公钥时启用逐字符加密；isSecure 由键盘内部控制，与加密同门控
    val encryptChar: ((String) -> String)? =
        if (activeField == "password" && pwdRsa != null) ({ ch -> RsaUtil.encryptChar(ch, pwdRsa!!) }) else null

    // 仅密码长度 6–30 才允许提交（web 行为；账号空由后端判）
    val pwdValid = pwd.length in 6..30

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgPage),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(80.dp))

            // 头部：logo + 标题 + 副标题（居中）
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandLogo()
                Spacer(Modifier.height(20.dp))
                BasicText(
                    "Gold 财管",
                    style = TextStyle(color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    "开启您的数字资产管理",
                    style = TextStyle(color = colors.textTertiary, fontSize = 14.sp),
                )
            }

            Spacer(Modifier.height(40.dp))

            // 账号字段
            LoginField(
                label = "账号",
                value = username,
                placeholder = "请输入账号或邮箱",
                leftIcon = "user-o",
                active = activeField == "username",
                onClick = { activeField = "username" },
            )
            Spacer(Modifier.height(12.dp))

            // 密码字段（掩码 / 眼睛切换）
            LoginField(
                label = "密码",
                value = if (reveal) pwd else "●".repeat(pwd.length),
                placeholder = "请输入密码",
                leftIcon = "lock",
                active = activeField == "password",
                rightIcon = if (reveal) "eye-o" else "closed-eye",
                onRightClick = { reveal = !reveal },
                onClick = {
                    activeField = "password"
                    // 激活密码框即异步取 RSA 公钥（握手缓存，失败则降级明文，对应 web 公钥未就绪）
                    scope.launch { pwdRsa = graph.handshake.rsa() }
                },
            )
            if (reveal) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "为了您的安全，请勿在公共场合下显示密码",
                    style = TextStyle(color = colors.danger, fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }

            Spacer(Modifier.height(30.dp))

            VanButton(
                text = if (loading) "正在安全登录..." else "立即登录",
                onClick = {
                    scope.launch {
                        loading = true
                        // 安全键盘密文数组优先；无密文（降级/账号框）时回退明文 password
                        val passwordPayload: Any = pwdCipher?.takeIf { it.isNotEmpty() } ?: pwd
                        when (val r = auth.login(username, passwordPayload)) {
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
                block = true,
                loading = loading,
                disabled = !pwdValid,
            )

            Spacer(Modifier.height(20.dp))

            if (AppConfig.useFake) {
                // 演示提示（对齐 web showDemoInfo）
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        "本站点仅作演示效果，推荐使用手机Chrome浏览器打开预览。锁定PIN码为 123456",
                        style = TextStyle(color = colors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                LoginFooter()
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // 安全键盘：激活字段时弹出；login 键关闭键盘（真正提交由上方按钮）
    VanSecureKeyboard(
        show = activeField != null,
        onChar = { ch ->
            if (activeField == "username") {
                username = (username + ch).take(50)
            } else if (activeField == "password") {
                pwd = (pwd + ch).take(30)
            }
        },
        onDelete = {
            if (activeField == "username") username = username.dropLast(1)
            else if (activeField == "password") pwd = pwd.dropLast(1)
        },
        encryptChar = encryptChar,
        onCipherAppend = { if (activeField == "password") pwdCipher = (pwdCipher ?: emptyList()) + it },
        onCipherDelete = { if (activeField == "password") pwdCipher = pwdCipher?.dropLast(1) },
        onLogin = { activeField = null },
    )
}

@Composable
private fun BrandLogo() {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "Gold 财管",
        modifier = Modifier.size(80.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun LoginFooter() {
    val colors = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "Gold 财管",
                modifier = Modifier.size(18.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(6.dp))
            BasicText(
                "Gold 财管",
                style = TextStyle(color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            "© 2026 Gold 财管",
            style = TextStyle(color = colors.textTertiary, fontSize = 11.sp),
        )
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    placeholder: String,
    leftIcon: String,
    active: Boolean,
    rightIcon: String? = null,
    onRightClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val borderColor = if (active) colors.primary else colors.border
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgCard, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (active) colors.primary else colors.textSecondary,
                fontSize = 14.sp,
            ),
            modifier = Modifier.width(46.dp),
        )
        Spacer(Modifier.width(8.dp))
        VanIcon(name = leftIcon, size = 18.sp, color = colors.textTertiary)
        Spacer(Modifier.width(8.dp))

        // 输入区 + 闪烁光标：光标随输入移动（对齐 web 登录记忆的坑——假光标须定位到
        // 文字末尾 left:curUser，否则「停滞在前」不跟手）。空值激活时落在首位（同 web 空串 0 宽）。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            val hasText = value.isNotEmpty()
            if (active && !hasText) {
                BlinkingCursor(colors.primary)
                Spacer(Modifier.width(2.dp))
            }
            BasicText(
                if (hasText) value else placeholder,
                style = TextStyle(
                    color = if (hasText) colors.textPrimary else colors.textPlaceholder,
                    fontSize = 16.sp,
                ),
            )
            if (active && hasText) {
                Spacer(Modifier.width(2.dp))
                BlinkingCursor(colors.primary)
            }
        }

        if (rightIcon != null) {
            Spacer(Modifier.width(8.dp))
            VanIcon(
                name = rightIcon,
                size = 18.sp,
                color = colors.primary,
                onClick = onRightClick,
            )
        }
    }
}

@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition("login-cursor")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
    )
    Box(
        Modifier
            .width(1.5.dp)
            .height(18.dp)
            .alpha(a)
            .background(color),
    )
}
