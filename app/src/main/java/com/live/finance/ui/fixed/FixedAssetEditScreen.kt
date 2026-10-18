package com.live.finance.ui.fixed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.live.finance.App
import com.live.finance.core.nav.Routes
import com.live.finance.core.net.ApiResult
import com.live.finance.core.net.failMessage
import com.live.finance.data.model.NewFixedAsset
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.common.AppButton
import com.live.finance.ui.common.AppButtonSize
import com.live.finance.ui.common.AppButtonType
import com.live.finance.ui.common.AppField
import com.live.finance.ui.common.ScreenScaffold
import com.live.finance.ui.flow.FText
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanPopup
import com.live.vant.form.VanPicker
import com.live.vant.form.VanPickerOption
import kotlinx.coroutines.launch
import com.live.finance.core.net.showFail
import com.live.vant.feedback.VanToastType

/**
 * 编辑固定资产（整页）—— 以 web `views/Finance/fixedAsset/Edit.vue` 为基准。
 *
 * 结构照抄 web：整页（非弹窗）；图片区 `padding 16 / bg-secondary / margin-bottom 12`；
 * 表单比登记弹窗多一行 **当前账面价值**（`now_val`，可手动调整）；底部只有**一个「保存」按钮**
 * （large/round/primary，非 block → 自然宽度左对齐）；保存成功后 `router.replace` 到详情页
 * （原生用 `popUpTo(列表) + navigate(详情)` 等价）。
 *
 * 与「登记」弹窗（`AssetForm.vue`）的差异，本工程按拍板做了**统一**（web 自身两处口径不一致）：
 *  1. 「预计使用年限」统一带上「推荐」按钮与 `推荐 N 年` 占位，选品类时自动带出推荐年限
 *     （web 编辑页原来没有，登记弹窗有）；
 *  2. 购买日期在编辑页**只读展示**：后端 `PUT /fixedAsset/:id` 的可改字段不含 `buy_date`
 *     （见 `api/src/modules/fixed_asset/model/fixed_asset.js` 的 allowedFields），
 *     因此编辑页不提交、也不校验它；
 *  3. 「当前账面价值」只存在于编辑页：后端新建接口不接受 `now_val`（新建时账面价值 = 购买价）。
 */
@Composable
fun FixedAssetEditScreen(nav: NavHostController, id: String) {
    val ctx = LocalContext.current
    val graph = App.of(ctx).graph
    val toast = LocalVanToastController.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var imgUrl by remember { mutableStateOf("") }
    var buyPrice by remember { mutableStateOf("") }
    var buyDate by remember { mutableStateOf("") }
    var useYears by remember { mutableStateOf("") }
    var residualRate by remember { mutableStateOf("5") }
    var secondhandPrice by remember { mutableStateOf("") }
    var nowVal by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    var showTagPicker by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }

    // 统一到登记弹窗口径：	agRecommendedYears[tag] || 3（web AssetForm.vue）
    val recommendedYears = TAG_RECOMMENDED_YEARS[tag] ?: 3

    // web `loadData`：`data.xxx || ''`（JS 假值：0 → 空串），residual_rate 兜底 '5'
    LaunchedEffect(id) {
        if (id.isBlank()) {
            toast.show("参数错误", VanToastType.Fail)
            nav.popBackStack()
            return@LaunchedEffect
        }
        when (val r = graph.fixed.detail(id)) {
            is ApiResult.Ok -> {
                val a = r.data
                if (a == null) {
                    toast.show("资产不存在")
                    nav.popBackStack()
                    return@LaunchedEffect
                }
                info = a.info
                tag = a.tag
                imgUrl = a.imgUrl
                buyPrice = if (a.buyPrice != 0.0) jsNum(a.buyPrice) else ""
                buyDate = a.buyDate.take(10)
                useYears = if (a.useYears != 0.0) jsNum(a.useYears) else ""
                residualRate = if (a.residualRate != 0.0) jsNum(a.residualRate) else "5"
                secondhandPrice = if (a.secondhandPrice != 0.0) jsNum(a.secondhandPrice) else ""
                nowVal = if (a.nowVal != 0.0) jsNum(a.nowVal) else ""
            }
            else -> toast.showFail(r, "加载失败")
        }
    }

    fun submit() {
        if (info.isEmpty()) return toast.show("请输入资产名称")
        if (tag.isEmpty()) return toast.show("请选择品类")
        val price = buyPrice.toDoubleOrNull() ?: 0.0
        if (price <= 0) return toast.show("购买价格必须大于0")
        val years = useYears.toDoubleOrNull() ?: 0.0
        if (years <= 0) return toast.show("预计使用年限必须大于0")
        val rate = residualRate.toDoubleOrNull() ?: -1.0
        if (rate < 0 || rate > 100) return toast.show("残值率必须在0~100之间")

        saving = true
        scope.launch {
            // ⚠ 编辑不提交 buy_date（后端可改字段里没有它），带 now_val
            val payload = NewFixedAsset(
                info = info,
                tag = tag,
                imgUrl = imgUrl,
                buyPrice = price,
                buyDate = buyDate,
                useYears = years,
                residualRate = rate,
                secondhandPrice = secondhandPrice.toDoubleOrNull(),
                nowVal = nowVal.toDoubleOrNull(),
            )
            when (val r = graph.fixed.update(id, payload)) {
                is ApiResult.Ok -> {
                    toast.success("保存成功")
                    // web `router.replace(detail)`：栈变成 [.., 列表, 详情]（详情会重新拉数据）
                    nav.navigate(Routes.fixedAssetDetail(id)) {
                        popUpTo(Routes.FIXED_ASSET) { inclusive = false }
                    }
                }
                is ApiResult.NeedPin -> {
                    saving = false
                    toast.show("需先验证 PIN")
                }
                else -> {
                    saving = false
                    toast.showFail(r, "保存失败")
                }
            }
        }
    }

    ScreenScaffold { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.bgPage)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            // .upload-section{padding 16; bg secondary; margin-bottom 12}
            Column(Modifier.fillMaxWidth().background(colors.bgCard).padding(16.dp)) {
                FText("资产图片", 16f, FontWeight.Normal, colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                UploadAreaBox(imgUrl) { showImagePicker = true }
            }
            Spacer(Modifier.height(12.dp))

            // van-cell-group inset（白卡圆角 8，左右内缩 16）
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
                FixedReadonlyField("购买日期", buyDate, "请选择") { }
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
                    trailing = { Suffix("元") },
                )
                AppField(
                    value = nowVal, onValueChange = { nowVal = it }, label = "当前账面价值",
                    placeholder = "选填，可手动调整", keyboardType = KeyboardType.Number,
                    trailing = { Suffix("元") }, border = false,
                )
            }

            // .form-actions{padding 16; margin-top 16}（web 未加 block → 自然宽度）
            Column(Modifier.fillMaxWidth().padding(16.dp).padding(top = 16.dp)) {
                AppButton(
                    text = "保存",
                    onClick = { submit() },
                    type = AppButtonType.Primary,
                    size = AppButtonSize.Large,
                    round = true,
                    loading = saving,
                )
            }
        }
    }

    // 品类选择（统一到登记弹窗口径：选品类且未填年限时自动带出推荐值）
    VanPopup(show = showTagPicker, onDismissRequest = { showTagPicker = false }, round = true) {
        VanPicker(
            columns = listOf(TAG_RECOMMENDED_YEARS.keys.map { VanPickerOption(it, it) }),
            title = "选择品类",
            onConfirm = { _, values ->
                val picked = values.firstOrNull()?.toString().orEmpty()
                tag = picked
                // 统一到登记弹窗口径：选了品类且还没填年限 → 自动带出推荐值
                if (useYears.isEmpty()) useYears = (TAG_RECOMMENDED_YEARS[picked] ?: 3).toString()
                showTagPicker = false
            },
            onCancel = { showTagPicker = false },
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
