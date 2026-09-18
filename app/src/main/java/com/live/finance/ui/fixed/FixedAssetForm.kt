package com.live.finance.ui.fixed

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.App
import com.live.finance.core.AppConfig
import com.live.finance.core.net.ApiResult
import com.live.finance.data.model.FixedAsset
import com.live.finance.data.model.NewFixedAsset
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.flow.FText
import com.live.vant.basic.VanImage
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import com.live.vant.icon.VanIcon
import com.live.vant.form.VanDatePicker
import kotlinx.coroutines.launch
import java.time.LocalDate

/** web `AssetForm.vue` / `Edit.vue` 共用的 `tagRecommendedYears`：品类 → 推荐使用年限。 */
internal val TAG_RECOMMENDED_YEARS: Map<String, Int> = linkedMapOf(
    "电脑" to 3, "手机" to 2, "平板" to 2, "外设" to 2, "家电" to 5, "家具" to 5, "其他" to 3,
)

/**
 * 固定资产登记/编辑表单弹窗 —— 一比一复刻 web `views/Finance/fixedAsset/components/AssetForm.vue`。
 *
 * 结构：底部圆角弹窗（`max-height 85vh` 内滚）＝ 标题（`登记固定资产` / `编辑资产`）+ `cross` →
 * 资产图片上传区（120×120 虚线框，有图则铺满，点击开 [FixedAssetImagePickerPopup]）→
 * `van-cell-group inset` 七行表单（资产名称 / 品类标签 / 购买价格 / 购买日期 /
 * 预计使用年限[带「推荐」] / 残值率 / 二手市场价）→ 取消·保存两键；另含品类 `van-picker` 与日期弹窗。
 *
 * 校验顺序与文案、提交 body 全部照抄 web。
 */
@Composable
fun FixedAssetFormPopup(
    show: Boolean,
    asset: FixedAsset?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    if (!show) return
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val maxFormH = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    var info by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var imgUrl by remember { mutableStateOf("") }
    var buyPrice by remember { mutableStateOf("") }
    var buyDate by remember { mutableStateOf("") }
    var useYears by remember { mutableStateOf("") }
    var residualRate by remember { mutableStateOf("5") }
    var secondhandPrice by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    var showTagPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }

    val recommendedYears = TAG_RECOMMENDED_YEARS[tag] ?: 3

    // web `watch(asset, {immediate:true})`：有值回填、无值重置（注意 `|| ''` / `|| '5'` 的 JS 假值语义）
    LaunchedEffect(asset?.id, show) {
        if (asset == null) {
            info = ""; tag = ""; imgUrl = ""; buyPrice = ""; buyDate = ""
            useYears = ""; residualRate = "5"; secondhandPrice = ""
        } else {
            info = asset.info
            tag = asset.tag
            imgUrl = asset.imgUrl
            buyPrice = if (asset.buyPrice != 0.0) jsNum(asset.buyPrice) else ""
            buyDate = asset.buyDate.take(10)
            useYears = if (asset.useYears != 0.0) jsNum(asset.useYears) else ""
            residualRate = if (asset.residualRate != 0.0) jsNum(asset.residualRate) else "5"
            secondhandPrice = if (asset.secondhandPrice != 0.0) jsNum(asset.secondhandPrice) else ""
        }
    }

    fun submit() {
        if (info.isEmpty()) return toast.show("请输入资产名称")
        if (tag.isEmpty()) return toast.show("请选择品类")
        val price = buyPrice.toDoubleOrNull() ?: 0.0
        if (price <= 0) return toast.show("购买价格必须大于0")
        if (buyDate.isEmpty()) return toast.show("请选择购买日期")
        val years = useYears.toDoubleOrNull() ?: 0.0
        if (years <= 0) return toast.show("预计使用年限必须大于0")
        val rate = residualRate.toDoubleOrNull() ?: -1.0
        if (rate < 0 || rate > 100) return toast.show("残值率必须在0~100之间")

        val payload = NewFixedAsset(
            info = info,
            tag = tag,
            imgUrl = imgUrl,
            buyPrice = price,
            buyDate = buyDate,
            useYears = years,
            residualRate = rate,
            secondhandPrice = secondhandPrice.toDoubleOrNull(),
        )
        saving = true
        scope.launch {
            val r = if (asset != null) graph.fixed.update(asset.id, payload) else graph.fixed.create(payload)
            saving = false
            when (r) {
                is ApiResult.Ok -> {
                    toast.success("保存成功")
                    onSuccess()
                    onDismiss()
                }
                is ApiResult.NeedPin -> toast.show("需先验证 PIN")
                else -> toast.show("保存失败")
            }
        }
    }

    VanPopup(show = true, onDismissRequest = onDismiss, round = true, closeOnClickOverlay = false) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxFormH)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // .popup-header：标题 17/600 + cross
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FText(if (asset != null) "编辑资产" else "登记固定资产", 17f, FontWeight.SemiBold, colors.textPrimary)
                Box(Modifier.clickable(onClick = onDismiss).padding(4.dp)) {
                    VanIcon(name = "cross", size = 18.sp, color = colors.textTertiary)
                }
            }

            // ===== 资产图片（.upload-section{padding 0 16}）=====
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FText("资产图片", 14f, FontWeight.Normal, colors.textSecondary)
                    Spacer(Modifier.width(4.dp))
                    FText("*", 14f, FontWeight.Normal, colors.danger)
                }
                Spacer(Modifier.height(8.dp))
                UploadAreaBox(imgUrl) { showImagePicker = true }
            }
            Spacer(Modifier.height(16.dp))

            // ===== 表单（van-cell-group inset：白卡圆角 8，左右内缩 16）=====
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bgCard),
            ) {
                AppField(value = info, onValueChange = { info = it }, label = "资产名称", placeholder = "如：MacBook Pro 14寸")
                FixedReadonlyField("品类标签", tag, "请选择品类") { showTagPicker = true }
                AppField(
                    value = buyPrice, onValueChange = { buyPrice = it }, label = "购买价格",
                    placeholder = "必须大于0", keyboardType = KeyboardType.Number,
                    trailing = { Suffix("元") },
                )
                FixedReadonlyField("购买日期", buyDate, "请选择") { showDatePicker = true }
                AppField(
                    value = useYears, onValueChange = { useYears = it }, label = "预计使用年限",
                    placeholder = "推荐 $recommendedYears 年", keyboardType = KeyboardType.Number,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Suffix("年")
                            AppButton(
                                text = "推荐", onClick = { useYears = recommendedYears.toString() },
                                type = AppButtonType.Primary, size = AppButtonSize.Small, plain = true,
                            )
                        }
                    },
                )
                AppField(
                    value = residualRate, onValueChange = { residualRate = it }, label = "残值率",
                    placeholder = "0~100，默认5%", keyboardType = KeyboardType.Number,
                    trailing = { Suffix("%") },
                )
                AppField(
                    value = secondhandPrice, onValueChange = { secondhandPrice = it }, label = "二手市场价",
                    placeholder = "选填", keyboardType = KeyboardType.Number,
                    trailing = { Suffix("元") }, border = false,
                )
            }

            // ===== 底部两键（.form-actions{gap 12; margin-top 20; padding 0 16 20}）=====
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppButton(text = "取消", onClick = onDismiss, size = AppButtonSize.Large, round = true, modifier = Modifier.weight(1f))
                AppButton(
                    text = "保存", onClick = { submit() }, type = AppButtonType.Primary,
                    size = AppButtonSize.Large, round = true, loading = saving, modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 品类选择（van-picker）
    VanPopup(show = showTagPicker, onDismissRequest = { showTagPicker = false }, round = true) {
        VanPicker(
            columns = listOf(TAG_RECOMMENDED_YEARS.keys.map { VanPickerOption(it, it) }),
            title = "选择品类",
            onConfirm = { _, values ->
                val picked = values.firstOrNull()?.toString().orEmpty()
                tag = picked
                // web：选了品类且还没填年限 → 自动带出推荐值
                if (useYears.isEmpty()) useYears = (TAG_RECOMMENDED_YEARS[picked] ?: 3).toString()
                showTagPicker = false
            },
            onCancel = { showTagPicker = false },
        )
    }

    // 购买日期（web 是 `<input type="date">` 浏览器原生日期选择；项目内其余 6 处同款字段（JobSetting 等）
    // 统一用 `VanDatePicker(type="date")`，故这里也对齐，不用整月日历 VanCalendar）
    if (showDatePicker) {
        VanDatePicker(
            type = "date",
            value = runCatching { LocalDate.parse(buyDate) }.getOrNull() ?: LocalDate.now(),
            onValueChange = {},
            title = "购买日期",
            onConfirm = { showDatePicker = false; buyDate = it.toString() },
            onCancel = { showDatePicker = false },
        )
    }

    FixedAssetImagePickerPopup(
        show = showImagePicker,
        current = imgUrl,
        onPick = {
            imgUrl = it
            showImagePicker = false
        },
        onDismiss = { showImagePicker = false },
    )
}

/** `.upload-area`（120×120 虚线框，有图铺满 / 无图 `photograph` 28 + 「点击选择图片」）。 */
@Composable
internal fun UploadAreaBox(imgUrl: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (imgUrl.isNotEmpty()) {
            VanImage(
                src = AppConfig.fullFileUrl(imgUrl),
                modifier = Modifier.fillMaxSize(),
                fit = ContentScale.Crop,
                showError = false,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VanIcon(name = "photograph", size = 28.sp, color = colors.textTertiary)
                Spacer(Modifier.height(4.dp))
                FText("点击选择图片", 12f, FontWeight.Normal, colors.textTertiary)
            }
        }
    }
}

/** `.app-field__suffix`：单位小字（tertiary、14px）。 */
@Composable
internal fun Suffix(text: String) {
    FText(text, 14f, FontWeight.Normal, LocalAppColors.current.textTertiary)
}
