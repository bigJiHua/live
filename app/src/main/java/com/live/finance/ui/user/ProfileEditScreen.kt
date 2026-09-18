package com.live.finance.ui.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppDialogShell
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanAction
import com.live.vant.feedback.VanActionSheet
import com.live.vant.form.VanSimpleKeyboard
import com.live.vant.icon.VanIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===== web `User/ProfileEdit.vue` 的度量常量（逐条对照其 scoped CSS）=====
private val AVATAR_SIZE_D = 100.dp      // van-image width/height="100"
private val CARD_SHAPE = RoundedCornerShape(12.dp)  // app-popup(center,round) border-radius:12px
private val FIELD_SHAPE = RoundedCornerShape(10.dp) // .field-wrap / .app-field border-radius:10px
private val GROUP_SHAPE = RoundedCornerShape(8.dp)  // van-cell-group inset 默认 8px（本页无 .app-card 覆写）

/** 安全键盘总高（4×58 + 3×7 + 工具栏），用于弹窗上移量：web `lift = kbH/2 + 8`。 */
private val KB_LIFT_D = 152.dp

/**
 * 编辑资料页 —— 1:1 复刻 web `src/views/User/ProfileEdit.vue`。
 *
 * 结构：`.avatar-section`（100 圆头像 + 相机角标 + 「点击头像更换」）
 *      → `.form-section` 用户名（app-field readonly is-link）
 *      → `.form-section` 邮箱地址
 *      → `.action-section` 修改登录密码（app-cell is-link）
 *      → 四个弹窗（头像 URL / 用户名 / 邮箱 / 密码）+ 内置 SimpleKeyboard 安全键盘。
 *
 * 契约：`PUT /user/profile`（仅 username|avatar）、`POST /user/email/send-code`（type=email|pwd）、
 *      `PUT /user/email/change`、`PUT /user/password/change`。
 */
@Composable
fun ProfileEditScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val ctx = LocalContext.current
    val toast = LocalVanToastController.current
    val graph = App.of(ctx).graph
    val scope = rememberCoroutineScope()

    // 当前用户（web 从 userStore 读；原生无 store，进页拉 /auth/me）
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }

    // 弹层
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showAvatarUrlDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var showPwdDialog by remember { mutableStateOf(false) }

    /** 当前激活字段（非空 = 安全键盘弹出）；取值见 web activeField。 */
    var activeField by remember { mutableStateOf<String?>(null) }
    fun closeKb() { activeField = null }

    // 各表单
    var avatarUrl by remember { mutableStateOf("") }
    var nameNew by remember { mutableStateOf("") }
    var emailNew by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var emailCountdown by remember { mutableIntStateOf(0) }
    var pwdOld by remember { mutableStateOf("") }
    var pwdNew by remember { mutableStateOf("") }
    var pwdConfirm by remember { mutableStateOf("") }
    var pwdCode by remember { mutableStateOf("") }
    var pwdCountdown by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (graph.auth.getUserinfo() as? ApiResult.Ok)?.data?.let {
            username = it.username
            email = it.email
            avatar = it.avatar
        }
    }

    // 倒计时（web：setInterval 每秒 -1，到 0 停）
    LaunchedEffect(emailCountdown) { if (emailCountdown > 0) { delay(1000); emailCountdown -= 1 } }
    LaunchedEffect(pwdCountdown) { if (pwdCountdown > 0) { delay(1000); pwdCountdown -= 1 } }

    // ===== 安全键盘输入分发（web activeValue / setActiveValue）=====
    fun onChar(ch: String) {
        when (activeField) {
            "name" -> nameNew = (nameNew + ch).take(20)              // maxlength 20
            "email" -> emailNew = (emailNew + ch).take(50)           // DB varchar(50)
            "emailCode" -> emailCode = (emailCode + ch).take(6)      // maxlength 6
            "pwdOld" -> pwdOld = (pwdOld + ch).take(30)
            "pwdNew" -> pwdNew = (pwdNew + ch).take(15)              // maxlength 15
            "pwdConfirm" -> pwdConfirm = (pwdConfirm + ch).take(15)
            "pwdCode" -> pwdCode = (pwdCode + ch).take(6)
            "avatarUrl" -> avatarUrl = (avatarUrl + ch).take(300)
        }
    }

    fun onDelete() {
        when (activeField) {
            "name" -> nameNew = nameNew.dropLast(1)
            "email" -> emailNew = emailNew.dropLast(1)
            "emailCode" -> emailCode = emailCode.dropLast(1)
            "pwdOld" -> pwdOld = pwdOld.dropLast(1)
            "pwdNew" -> pwdNew = pwdNew.dropLast(1)
            "pwdConfirm" -> pwdConfirm = pwdConfirm.dropLast(1)
            "pwdCode" -> pwdCode = pwdCode.dropLast(1)
            "avatarUrl" -> avatarUrl = avatarUrl.dropLast(1)
        }
    }

    // ===== 相册选图（native 增强：web 只有「输入图片 URL」）=====
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val picked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.live.finance.ui.common.compressUriToJpeg(ctx, uri, maxSide = 512)
                }
                if (picked == null) { toast.show("读取图片失败"); uploading = false; return@launch }
                val r = graph.client.uploadRaw(
                    "/upload/single", "file", picked.fileName, picked.mime, picked.bytes,
                    mapOf("busType" to "other"),
                )
                uploading = false
                when (r) {
                    is ApiResult.Ok -> {
                        val path = r.data?.get("file_path")?.takeIf { !it.isJsonNull }?.asString
                            ?: r.data?.get("url")?.takeIf { !it.isJsonNull }?.asString
                        if (path == null) { toast.show("上传返回空"); return@launch }
                        // 必须转成绝对 URL：后端 avatar 走 Joi `string().uri()`，相对路径会被判「格式不正确」
                        val abs = AppConfig.fullFileUrl(path)
                        when (val up = graph.auth.updateProfile(avatar = abs)) {
                            is ApiResult.Ok -> { up.data?.let { avatar = it.avatar }; toast.show("头像已更新") }
                            is ApiResult.Fail -> toast.show(up.message)
                            else -> toast.show("头像更新失败")
                        }
                    }
                    is ApiResult.Fail -> toast.show(r.message)
                    else -> toast.show("上传失败")
                }
            }
        }
    }

    // ===== 提交逻辑（逐条对齐 web 的 handleXxx）=====
    fun submitAvatarUrl() {
        val url = avatarUrl.trim()
        if (url.isEmpty()) return // web: if (!url) return;
        scope.launch {
            when (val r = graph.auth.updateProfile(avatar = url)) {
                is ApiResult.Ok -> {
                    r.data?.let { avatar = it.avatar }
                    toast.show("头像已更新")
                    avatarUrl = ""
                }
                is ApiResult.Fail -> toast.show(r.message)
                else -> toast.show("头像更新失败")
            }
        }
    }

    /** web `usernameRules`：3-20 位、支持中英文、不支持特殊符号。 */
    fun usernameRulesOk(): Boolean {
        val lenOk = nameNew.length in 3..20
        val charsOk = nameNew.isNotEmpty() && nameNew.all { it.isLetterOrDigit() || it.code in 0x4E00..0x9FA5 }
        return lenOk && charsOk
    }

    fun submitName(onDone: () -> Unit) {
        if (!usernameRulesOk()) { toast.show("请检查用户名规则"); onDone(); return }
        scope.launch {
            when (val r = graph.auth.updateProfile(username = nameNew)) {
                is ApiResult.Ok -> {
                    r.data?.let { username = it.username }
                    toast.show("用户名修改成功")
                }
                is ApiResult.Fail -> toast.show(r.message)
                else -> toast.show("用户名修改失败")
            }
            onDone()
        }
    }

    fun sendEmailCode() {
        if (emailNew.isEmpty()) { toast.show("请输入新邮箱"); return }
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(emailNew)) { toast.show("邮箱格式不正确"); return }
        scope.launch {
            when (val r = graph.auth.sendEmailCode(emailNew, "email")) {
                is ApiResult.Ok -> { toast.show("验证码已发送"); emailCountdown = 60 }
                is ApiResult.Fail -> toast.show(r.message)
                else -> toast.show("发送失败")
            }
        }
    }

    /** web `handleEmailVerify` 成功才关弹窗（before-close 语义），失败保持打开。 */
    fun submitEmail(onSuccess: () -> Unit) {
        if (submitting) return
        submitting = true
        scope.launch {
            when (val r = graph.auth.changeEmail(emailNew, emailCode)) {
                is ApiResult.Ok -> {
                    r.data?.let { email = it.email }
                    toast.show("邮箱修改成功")
                    submitting = false
                    onSuccess()
                }
                is ApiResult.Fail -> { toast.show(r.message); submitting = false }
                else -> { toast.show("邮箱修改失败"); submitting = false }
            }
        }
    }

    fun sendPwdCode() {
        if (pwdOld.isBlank() || pwdNew.isBlank() || pwdConfirm.isBlank()) { toast.show("请先填写完整信息"); return }
        if (pwdNew != pwdConfirm) { toast.show("两次输入的新密码不一致"); return }
        scope.launch {
            // type=pwd：后端从库里取绑定邮箱，忽略传入 email
            when (val r = graph.auth.sendEmailCode(email, "pwd")) {
                is ApiResult.Ok -> { toast.show("验证码已发送"); pwdCountdown = 60 }
                is ApiResult.Fail -> toast.show(r.message)
                else -> toast.show("发送失败")
            }
        }
    }

    fun submitPassword(onSuccess: () -> Unit) {
        if (submitting) return
        if (pwdOld.isEmpty() || pwdNew.isEmpty() || pwdCode.isEmpty()) { toast.show("请填写完整信息"); return }
        if (pwdNew != pwdConfirm) { toast.show("新输入的两次密码不一致"); return }
        submitting = true
        scope.launch {
            when (val r = graph.auth.changePassword(pwdOld, pwdNew, pwdCode)) {
                is ApiResult.Ok -> {
                    submitting = false
                    toast.show("修改成功，请重新登录")
                    pwdCountdown = 0
                    onSuccess()
                    delay(1200) // web: setTimeout 1200 → 回首页
                    graph.auth.logout()
                    nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                }
                is ApiResult.Fail -> { toast.show(r.message); submitting = false }
                else -> { toast.show("密码修改失败"); submitting = false }
            }
        }
    }

    // ===== 页面 =====
    ScreenScaffold { inner ->
        Column(
            modifier = inner
                .verticalScroll(rememberScrollState())
                .padding(bottom = 30.dp), // .page-profile-edit padding-bottom: 30px
        ) {
            // 页面标题（web 在全局 nav-bar 上；原生目前无全局栏，沿用本项目各页自绘标题的约定）
            FText(
                "编辑资料", 18f, FontWeight.Bold, colors.textPrimary,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            )

            // 1) 头像区
            AvatarSection(
                avatarUrl = if (avatar.isBlank()) "" else AppConfig.fullFileUrl(avatar),
                uploading = uploading,
                onEditClick = { showAvatarSheet = true },
            )

            // 2) 用户名（只读展示 + 点击修改）—— `.form-section{ margin-bottom:12px }`
            FormGroup {
                ReadonlyLinkField(
                    label = "用户名",
                    value = username,
                    onClick = { nameNew = username; showNameDialog = true },
                )
            }
            Spacer(Modifier.height(12.dp))

            // 3) 邮箱地址
            FormGroup {
                ReadonlyLinkField(
                    label = "邮箱地址",
                    value = email,
                    onClick = {
                        emailNew = ""
                        emailCode = ""
                        showEmailDialog = true
                    },
                )
            }
            Spacer(Modifier.height(12.dp))

            // 4) 修改登录密码 —— `.action-section{ margin-bottom:20px }`
            FormGroup {
                LinkCell(
                    title = "修改登录密码",
                    onClick = {
                        pwdOld = ""; pwdNew = ""; pwdConfirm = ""; pwdCode = ""
                        showPwdDialog = true
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // ===== 头像更换 ActionSheet（web：输入图片 URL / 取消；native 额外提供相册）=====
    VanActionSheet(
        show = showAvatarSheet,
        onDismissRequest = { showAvatarSheet = false },
        actions = listOf(
            VanAction(name = "从相册选择"), // native 增强（web 无此项）
            VanAction(name = "输入图片 URL"),
            VanAction(name = "取消", color = colors.danger),
        ),
        closeOnClickAction = true,
        onSelect = { action, index ->
            showAvatarSheet = false
            when (index) {
                0 -> avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                1 -> showAvatarUrlDialog = true
            }
        },
    )

    // ===== 头像 URL 弹窗（无 before-close：确认即关）=====
    EditDialog(
        show = showAvatarUrlDialog,
        title = "输入头像 URL",
        activeField = activeField,
        onOverlay = { closeKb(); showAvatarUrlDialog = false },
        onCancel = { closeKb(); showAvatarUrlDialog = false },
        onConfirm = { closeKb(); submitAvatarUrl(); showAvatarUrlDialog = false },
        onChar = ::onChar,
        onDelete = ::onDelete,
        onKbConfirm = { closeKb() },
    ) {
        DialogField(
            label = "",
            value = avatarUrl,
            placeholder = "请输入图片链接",
            active = activeField == "avatarUrl",
            maxlength = 300,
            onClick = { activeField = "avatarUrl" },
        )
    }

    // ===== 修改用户名弹窗 =====
    EditDialog(
        show = showNameDialog,
        title = "修改用户名",
        activeField = activeField,
        onOverlay = { closeKb(); showNameDialog = false },
        onCancel = { closeKb(); showNameDialog = false },
        // web 该弹窗无 before-close：校验失败也会关闭
        onConfirm = { closeKb(); showNameDialog = false; submitName {} },
        onChar = ::onChar,
        onDelete = ::onDelete,
        onKbConfirm = { closeKb() },
    ) {
        DialogField(
            label = "新用户名",
            value = nameNew,
            placeholder = "请输入新用户名",
            active = activeField == "name",
            maxlength = 20,
            onClick = { activeField = "name" },
        )
        RuleBox(
            title = "用户名要求:",
            items = listOf(
                "✔ 长度 3-20 位" to (nameNew.length in 3..20),
                "✔ 支持中文" to nameNew.any { it.code in 0x4E00..0x9FA5 },
                "✔ 支持英文" to nameNew.any { it.isLetter() && it.code < 0x4E00 },
                "✔ 不支持特殊符号" to (nameNew.isNotEmpty() && nameNew.all { it.isLetterOrDigit() || it.code in 0x4E00..0x9FA5 }),
            ),
        )
    }

    // ===== 修改邮箱弹窗（before-close：成功才关）=====
    EditDialog(
        show = showEmailDialog,
        title = "修改邮箱",
        activeField = activeField,
        // web 点遮罩同样走 before-close → 尝试提交；失败保持打开
        onOverlay = { closeKb(); submitEmail { showEmailDialog = false } },
        onCancel = { closeKb(); showEmailDialog = false },
        onConfirm = { closeKb(); submitEmail { showEmailDialog = false } },
        onChar = ::onChar,
        onDelete = ::onDelete,
        onKbConfirm = { closeKb() },
    ) {
        DialogField(
            label = "新邮箱",
            value = emailNew,
            placeholder = "请输入新邮箱",
            active = activeField == "email",
            maxlength = 50,
            onClick = { activeField = "email" },
        )
        Spacer(Modifier.height(12.dp))
        DialogField(
            label = "验证码",
            value = emailCode,
            placeholder = "请输入验证码",
            active = activeField == "emailCode",
            maxlength = 6,
            onClick = { activeField = "emailCode" },
            rightSlot = {
                SmallPrimaryButton(
                    text = if (emailCountdown > 0) "${emailCountdown}s" else "发送验证码",
                    enabled = emailCountdown <= 0,
                    onClick = { sendEmailCode() },
                )
            },
        )
    }

    // ===== 修改登录密码弹窗（before-close；键盘弹出时点遮罩不关）=====
    EditDialog(
        show = showPwdDialog,
        title = "修改登录密码",
        activeField = activeField,
        onOverlay = { if (activeField == null) { closeKb(); showPwdDialog = false } else closeKb() },
        onCancel = { closeKb(); showPwdDialog = false },
        onConfirm = { closeKb(); submitPassword { showPwdDialog = false } },
        onChar = ::onChar,
        onDelete = ::onDelete,
        onKbConfirm = { closeKb() },
    ) {
        DialogField(
            label = "原密码",
            value = "●".repeat(pwdOld.length),
            placeholder = "请输入原密码",
            active = activeField == "pwdOld",
            maxlength = 30,
            onClick = { activeField = "pwdOld" },
        )
        Spacer(Modifier.height(12.dp))
        DialogField(
            label = "新密码",
            value = "●".repeat(pwdNew.length),
            placeholder = "请输入新密码",
            active = activeField == "pwdNew",
            maxlength = 15,
            onClick = { activeField = "pwdNew" },
        )
        Spacer(Modifier.height(12.dp))
        DialogField(
            label = "确认新密码",
            value = "●".repeat(pwdConfirm.length),
            placeholder = "请再次输入新密码",
            active = activeField == "pwdConfirm",
            maxlength = 15,
            onClick = { activeField = "pwdConfirm" },
        )
        Spacer(Modifier.height(12.dp))
        DialogField(
            label = "验证码",
            value = pwdCode,
            placeholder = "请输入验证码",
            active = activeField == "pwdCode",
            maxlength = 6,
            onClick = { activeField = "pwdCode" },
            rightSlot = {
                // web canSendPasswordCode：三项填齐 + 两次新密码一致 + 倒计时结束
                SmallPrimaryButton(
                    text = if (pwdCountdown > 0) "${pwdCountdown}s" else "发送验证码",
                    enabled = pwdCountdown <= 0 &&
                        pwdOld.isNotBlank() && pwdNew.isNotBlank() && pwdConfirm.isNotBlank() &&
                        pwdNew == pwdConfirm,
                    onClick = { sendPwdCode() },
                )
            },
        )
    }
}

/**
 * 头像区 —— web `.avatar-section`：竖排居中、`padding: 40px 20px 30px`；
 * 100 圆头像（`.avatar{ border:3px solid var(--theme-bg-secondary); box-shadow: 0 4px 12px rgba(0,0,0,.1) }`）；
 * 右下 `.avatar-edit-btn`（32 圆、主色底、2px 白边、photograph 20）；下 `.avatar-tip`（mt 12 / 12px / text-tertiary）。
 */
@Composable
private fun AvatarSection(avatarUrl: String, uploading: Boolean, onEditClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 30.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(AVATAR_SIZE_D)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color(0x1A000000), // 0 4px 12px rgba(0,0,0,.1)
                        spotColor = Color(0x1A000000),
                    )
                    .border(3.dp, colors.bgCard, CircleShape), // border: 3px solid bg-secondary
            ) {
                // web 用 userStore.avatar（**无**默认头像兜底）；空白即空环
                VanImage(
                    src = avatarUrl,
                    width = AVATAR_SIZE_D,
                    height = AVATAR_SIZE_D,
                    round = true,
                    showError = false,
                )
            }
            // 相机角标（右下）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .border(2.dp, Color.White, CircleShape)
                    .clickable(enabled = !uploading) { onEditClick() },
            ) {
                VanIcon(name = "photograph", size = 20.sp, color = colors.buttonPrimaryText)
            }
        }
        Spacer(Modifier.height(12.dp))
        FText(
            if (uploading) "上传中…" else "点击头像更换",
            12f, FontWeight.Normal, colors.textTertiary,
        )
    }
}

/** van-cell-group（inset，圆角 8、左右 16 外边距、白卡底）；本页三处分组共用。 */
@Composable
private fun FormGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(GROUP_SHAPE)
            .background(LocalAppColors.current.bgCard),
        content = content,
    )
}

/**
 * 只读展示字段 —— 对应 web `<app-field readonly is-link :model-value>`：
 * `.app-field{padding:10px 16px; border-bottom:1px solid border; font-size:14px}`，
 * label 13px text-secondary（mb 6），值 14px text-primary，右侧 arrow 16 text-tertiary。
 */
@Composable
private fun ReadonlyLinkField(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        FText(label, 13f, FontWeight.Normal, colors.textSecondary, Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FText(value, 14f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            VanIcon(name = "arrow", size = 16.sp, color = colors.textTertiary)
        }
    }
    // .app-field:not(--borderless){ border-bottom: 1px solid var(--theme-border) }（通栏）
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

/** app-cell（12/16、14px、通栏底线、箭头）；本页「修改登录密码」用。 */
@Composable
private fun LinkCell(title: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FText(title, 14f, FontWeight.Normal, colors.textPrimary, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        VanIcon(name = "arrow", size = 16.sp, color = colors.textTertiary)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

/**
 * 本页弹窗 —— 复用共享底座 [AppDialogShell]（= web `AppPopup(center,round)` + `AppDialog`）。
 * 安全键盘**内嵌在同一弹窗内**（与 web 同层），故键盘弹出时整卡上移 `KB_LIFT_D`；内容区限高可滚动。
 */
@Composable
private fun EditDialog(
    show: Boolean,
    title: String,
    activeField: String?,
    onOverlay: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onChar: (String) -> Unit,
    onDelete: () -> Unit,
    onKbConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val screenH = LocalConfiguration.current.screenHeightDp
    val kbUp = activeField != null
    AppDialogShell(
        show = show,
        title = title,
        confirmText = "确认修改",
        onOverlay = onOverlay,
        onCancel = onCancel,
        onConfirm = onConfirm,
        lift = if (kbUp) KB_LIFT_D else 0.dp,
        // 键盘占位后卡片可用高度：屏高 - 键盘/标题/底栏余量
        maxContentHeight = if (kbUp) (screenH - 374).coerceAtLeast(140).dp else (screenH - 160).dp,
        bottomOverlay = if (kbUp) {
            {
                VanSimpleKeyboard(
                    onChar = onChar,
                    onDelete = onDelete,
                    onConfirm = onKbConfirm,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else null,
        content = content,
    )
}

/**
 * 弹窗内字段 —— 对应 `.field-wrap :deep(.app-field)`：
 * 透明底 + 1px border + 圆角 10；激活时描边主色 + 2px 主色内环、label 变主色；
 * label 13px（mb 6），值 14px/行高 1.5（→21sp），激活时值后跟闪烁光标。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun DialogField(
    label: String,
    value: String,
    placeholder: String,
    active: Boolean,
    /** 对应 web 的 `:maxlength`（仅声明口径；真正截断在 onChar 内按字段完成）。 */
    maxlength: Int,
    onClick: () -> Unit,
    rightSlot: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FIELD_SHAPE)
            .background(Color.Transparent)
            // 1px border + inset 2px 主色环 ≈ 3px 主色描边（Compose border 不占布局，切换无跳动）
            .border(if (active) 3.dp else 1.dp, if (active) colors.primary else colors.border, FIELD_SHAPE)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (label.isNotEmpty()) {
            FText(
                label, 13f, FontWeight.Normal,
                if (active) colors.primary else colors.textSecondary,
                Modifier.padding(bottom = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                FText(
                    if (value.isEmpty()) placeholder else value,
                    14f, FontWeight.Normal,
                    if (value.isEmpty()) colors.textPlaceholder else colors.textPrimary,
                    Modifier.padding(vertical = 2.dp),
                )
                if (active) {
                    Spacer(Modifier.width(1.dp))
                    BlinkingCursor(colors.primary)
                }
            }
            if (rightSlot != null) {
                Spacer(Modifier.width(6.dp))
                rightSlot()
            }
        }
    }
}

/** `.field-cursor`：2×18 主色竖条，1s step-end 闪烁（此处用 1s 线性往返近似）。 */
@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition("profile-cursor")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
    )
    Box(
        Modifier
            .width(2.dp)
            .height(18.dp)
            .alpha(a)
            .background(color),
    )
}

/** 规则框 —— web `.username-rules`：bg-tertiary、圆角 8、padding 12、mt 12；strong 12/600 主文色；li 13，命中 success/500，未命中 text-tertiary。 */
@Composable
private fun RuleBox(title: String, items: List<Pair<String, Boolean>>) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgThird)
            .padding(12.dp),
    ) {
        FText(title, 12f, FontWeight.SemiBold, colors.textPrimary, Modifier.padding(bottom = 8.dp))
        items.forEach { (text, ok) ->
            FText(
                text, 13f,
                if (ok) FontWeight.Medium else FontWeight.Normal,
                if (ok) colors.success else colors.textTertiary,
                Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

/** app-button small primary（「发送验证码」）：高 34、圆角 8、字号 13、主色底、禁用 0.55。 */
@Composable
private fun SmallPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(34.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.primary)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
    ) {
        FText(text, 13f, FontWeight.Medium, colors.buttonPrimaryText)
    }
}
