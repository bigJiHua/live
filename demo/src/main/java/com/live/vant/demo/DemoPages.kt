package com.live.vant.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.vant.basic.*
import com.live.vant.feedback.*
import com.live.vant.form.*
import com.live.vant.icon.VanIcon
import com.live.vant.layout.VanCol
import com.live.vant.layout.VanRow
import com.live.vant.nav.*
import com.live.vant.other.VanCalendar
import com.live.vant.other.VanSwipe
import com.live.vant.theme.LocalVantColors

private val PageNames = listOf("基础", "表单", "反馈", "导航", "其它")

/** 底部 Tabbar 切换 5 类组件展示页（自身即 van-tabbar 复刻） */
@Composable
fun DemoShell() {
    var tab by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(LocalVantColors.current.bgPage)) {
        Column(Modifier.fillMaxSize()) {
            VanNavBar(title = "Vant 组件复刻 Demo", leftText = null)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> BasicPage()
                    1 -> FormPage()
                    2 -> FeedbackPage()
                    3 -> NavPage()
                    else -> OtherPage()
                }
            }
        }
        VanTabbar(
            active = tab,
            onActiveChange = { tab = it },
            items = listOf(
                VanTabbarItemData(icon = "wap-home-o", title = "基础"),
                VanTabbarItemData(icon = "edit", title = "表单"),
                VanTabbarItemData(icon = "chat-o", title = "反馈"),
                VanTabbarItemData(icon = "bars", title = "导航"),
                VanTabbarItemData(icon = "apps-o", title = "其它"),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 60.dp),
    ) { content() }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.BasicText(
            title,
            style = TextStyle(color = LocalVantColors.current.textTertiary, fontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
    Spacer(Modifier.height(8.dp))
}

/* ============================= 基础 ============================= */

@Composable
private fun BasicPage() = Page {
    Section("Icon 图标（van-icon 字体 1:1）") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
        ) {
            listOf("arrow", "cross", "plus", "success", "info-o", "warning-o", "clock-o", "location-o", "edit", "search").forEach {
                VanIcon(name = it, size = 24.sp, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
    Section("Button 按钮") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VanButton("主要", {}, type = VanButtonType.Primary)
                Spacer(Modifier.width(8.dp))
                VanButton("成功", {}, type = VanButtonType.Success)
                Spacer(Modifier.width(8.dp))
                VanButton("危险", {}, type = VanButtonType.Danger)
                Spacer(Modifier.width(8.dp))
                VanButton("线框", {}, plain = true, type = VanButtonType.Primary)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VanButton("圆角", {}, round = true, type = VanButtonType.Primary)
                Spacer(Modifier.width(8.dp))
                VanButton("加载", {}, loading = true, type = VanButtonType.Primary)
                Spacer(Modifier.width(8.dp))
                VanButton("禁用", {}, disabled = true, type = VanButtonType.Primary)
                Spacer(Modifier.width(8.dp))
                VanButton("小", {}, size = VanButtonSize.Small)
                VanButton("迷你", {}, size = VanButtonSize.Mini)
            }
            Spacer(Modifier.height(8.dp))
            VanButton("块级按钮", {}, block = true, type = VanButtonType.Primary)
        }
    }
    Section("Cell / CellGroup") {
        VanCellGroup(title = "分组标题", inset = true) {
            VanCell(title = "标题", value = "内容")
            VanCell(title = "带箭头", value = "内容", isLink = true, onClick = {})
            VanCell(title = "带说明", label = "说明文字", value = "内容")
            VanCell(title = "带图标", icon = "location-o", value = "内容", center = true)
        }
    }
    Section("Loading / Divider / Tag") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
        ) {
            VanLoading(size = 24.sp)
            Spacer(Modifier.width(12.dp))
            VanLoading(size = 24.sp, type = VanLoadingType.Circular, color = LocalVantColors.current.primary)
            Spacer(Modifier.width(12.dp))
            VanLoading(size = 20.sp, vertical = true, text = "加载中")
            Spacer(Modifier.width(16.dp))
            VanTag("标签")
            Spacer(Modifier.width(6.dp))
            VanTag("primary", type = VanTagType.Primary)
            Spacer(Modifier.width(6.dp))
            VanTag("plain", type = VanTagType.Danger, plain = true, round = true)
        }
    }
    Section("Progress / Circle / NoticeBar") {
        Column(Modifier.background(Color.White).padding(16.dp)) {
            VanProgress(percentage = 60f, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            VanCircle(rate = 70f, text = "70%", size = 60.dp)
            Spacer(Modifier.height(12.dp))
            VanNoticeBar("这是一条通知栏文字，可配置滚动与左侧图标", leftIcon = "volume-o", mode = "closeable")
        }
    }
    Section("Skeleton / Empty") {
        Column(Modifier.background(Color.White).padding(16.dp)) {
            VanSkeleton(loading = true, row = 3, title = true) {}
            Spacer(Modifier.height(12.dp))
            VanEmpty(description = "暂无数据")
        }
    }
    Section("Image") {
        Row(Modifier.background(Color.White).padding(16.dp)) {
            VanImage(
                src = "https://picsum.photos/seed/vant/200/200",
                width = 80.dp, height = 80.dp, radius = 8.dp,
            )
            Spacer(Modifier.width(8.dp))
            VanImage(src = "https://bad.url/x.png", width = 80.dp, height = 80.dp, round = true)
        }
    }
}

/* ============================= 表单 ============================= */

@Composable
private fun FormPage() = Page {
    var text by remember { mutableStateOf("") }
    var number by remember { mutableStateOf(0) }
    var switchOn by remember { mutableStateOf(true) }
    var radioVal by remember { mutableStateOf("a") }
    var checkVals by remember { mutableStateOf(listOf("1")) }
    var stepper by remember { mutableStateOf(1) }
    var showPicker by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val toast = LocalVanToastController.current

    Section("Field 输入框") {
        Column(Modifier.background(Color.White)) {
            VanField(value = text, onValueChange = { text = it }, label = "标签", placeholder = "请输入内容", clearable = true)
            VanField(value = "", onValueChange = {}, label = "密码", placeholder = "请输入密码", type = VanFieldType.Password)
            VanField(value = "只读", onValueChange = {}, label = "选择", readonly = true, onClick = {})
        }
    }
    Section("Stepper / Switch") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
        ) {
            VanStepper(value = stepper, onValueChange = { stepper = it }, min = 0, max = 10)
            Spacer(Modifier.width(24.dp))
            VanSwitch(checked = switchOn, onCheckedChange = { switchOn = it }, size = 24)
        }
    }
    Section("Radio / Checkbox") {
        Column(Modifier.background(Color.White).padding(16.dp)) {
            VanRadioGroup(value = radioVal, onValueChange = { radioVal = it }, direction = "horizontal") {
                VanRadio(name = "a", label = "选项 A")
                VanRadio(name = "b", label = "选项 B")
            }
            Spacer(Modifier.height(12.dp))
            VanCheckboxGroup(value = checkVals, onValueChange = { checkVals = it }, direction = "horizontal") {
                VanCheckbox(name = "1", label = "复选 1")
                VanCheckbox(name = "2", label = "复选 2")
            }
        }
    }
    Section("Picker / DatePicker") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            VanButton("打开 Picker", { showPicker = true }, type = VanButtonType.Primary, block = true)
            Spacer(Modifier.height(8.dp))
            VanDatePicker(type = "date", value = java.time.LocalDate.now(), onValueChange = {})
        }
        val pickerColumns = remember {
            listOf(listOf("杭州", "绍兴", "宁波").map { VanPickerOption(it) })
        }
        if (showPicker) {
            VanPopup(
                show = true,
                onDismissRequest = { showPicker = false },
                position = VanPopupPosition.Bottom,
            ) {
                Column(Modifier.background(LocalVantColors.current.bgCard)) {
                    VanPicker(
                        columns = pickerColumns,
                        onConfirm = { idx, vals ->
                            showPicker = false
                            toast.success("选中: ${vals.joinToString()}")
                        },
                        onCancel = { showPicker = false },
                        title = "城市选择",
                    )
                }
            }
        }
    }
    Section("NumberKeyboard + PasswordInput") {
        var kbText by remember { mutableStateOf("") }
        var showKb by remember { mutableStateOf(false) }
        Column(Modifier.background(Color.White).padding(16.dp)) {
            VanPasswordInput(value = kbText, length = 6, focused = showKb)
            Spacer(Modifier.height(12.dp))
            VanButton(
                text = if (showKb) "收起键盘" else "调起数字键盘",
                onClick = { showKb = !showKb },
            )
        }
        VanNumberKeyboard(
            show = showKb,
            text = kbText,
            onTextChange = { kbText = it },
            theme = VanKeyboardTheme.Custom,
            closeButtonText = "完成",
            maxLength = 6,
            onClose = { showKb = false },
        )
    }
    Section("Search") {
        VanSearch(
            value = search,
            onValueChange = { search = it },
            placeholder = "请输入查询内容",
            shape = "round",
            showAction = true,
            onCancel = { search = "" },
            onSearch = { toast.show("搜索: $it") },
        )
    }
}

/* ============================= 反馈 ============================= */

@Composable
private fun FeedbackPage() = Page {
    val toast = LocalVanToastController.current
    var showConfirm by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showCal by remember { mutableStateOf(false) }

    Section("Toast（showToast / showSuccessToast / showLoadingToast / closeToast）") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            Row {
                VanButton("文字", { toast.show("这是一条提示") })
                Spacer(Modifier.width(8.dp))
                VanButton("成功", { toast.success("操作成功") })
                Spacer(Modifier.width(8.dp))
                VanButton("失败", { toast.fail("操作失败") })
            }
            Spacer(Modifier.height(8.dp))
            Row {
                VanButton("加载中", { toast.loading("加载中...") })
                Spacer(Modifier.width(8.dp))
                VanButton("关闭", { toast.close() })
                Spacer(Modifier.width(8.dp))
                VanButton("顶部", { toast.show("顶部提示", position = VanToastPosition.Top) })
            }
        }
    }
    Section("Dialog（showConfirmDialog / showDialog）") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            VanButton("确认对话框", { showConfirm = true }, type = VanButtonType.Primary, block = true)
        }
        VanConfirmDialog(
            show = showConfirm,
            title = "提示",
            message = "确认要执行此操作吗？",
            onClose = { showConfirm = false },
            onConfirm = { showConfirm = false; toast.success("已确认") },
        )
    }
    Section("Popup 弹出层") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            VanButton("底部弹出", { showPopup = true })
            Spacer(Modifier.height(8.dp))
            VanButton("ActionSheet", { showSheet = true })
            Spacer(Modifier.height(8.dp))
            VanButton("图片预览", { showPreview = true })
        }
        VanPopup(show = showPopup, onDismissRequest = { showPopup = false }, round = true) {
            Column(Modifier.padding(24.dp)) {
                androidx.compose.foundation.text.BasicText("这是一个底部 Popup 内容区", style = TextStyle(fontSize = 14.sp))
            }
        }
        VanActionSheet(
            show = showSheet,
            onDismissRequest = { showSheet = false },
            title = "请选择操作",
            cancelText = "取消",
            actions = listOf(
                VanAction("编辑", subname = "修改内容", icon = "edit"),
                VanAction("删除", color = LocalVantColors.current.danger, icon = "delete-o"),
                VanAction("禁用项", disabled = true),
            ),
            onSelect = { a, _ -> showSheet = false; toast.show("点了 ${a.name}") },
        )
        VanImagePreview(
            show = showPreview,
            images = listOf("https://picsum.photos/seed/a/800/800", "https://picsum.photos/seed/b/800/800"),
            closeable = true,
            onClose = { showPreview = false },
        )
    }
    Section("PullRefresh / List") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            androidx.compose.foundation.text.BasicText("下拉刷新与无限滚动在首页演示（见「其它」页）。", style = TextStyle(color = LocalVantColors.current.textTertiary, fontSize = 14.sp))
        }
    }
}

/* ============================= 导航 ============================= */

@Composable
private fun NavPage() = Page {
    var tabActive by remember { mutableStateOf("tab1") }
    var collapse by remember { mutableStateOf(listOf("1")) }
    var d1 by remember { mutableStateOf("全部") }

    Section("Tabs 选项卡") {
        Column(Modifier.background(Color.White).height(120.dp)) {
            VanTabs(
                active = tabActive,
                onActiveChange = { tabActive = it },
                tabs = listOf(
                    VanTabItem("tab1", "标签一"),
                    VanTabItem("tab2", "标签二"),
                    VanTabItem("tab3", "标签三", badge = "5"),
                ),
            ) { name ->
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.text.BasicText(name)
                }
            }
        }
    }
    Section("Collapse 折叠面板") {
        Column(Modifier.background(Color.White)) {
            VanCollapse(activeNames = collapse, onActiveNamesChange = { collapse = it }) {
                VanCollapseItem(name = "1", title = "标题一", icon = "question-o") {
                    androidx.compose.foundation.text.BasicText("这是第一块内容区域。", style = TextStyle(color = LocalVantColors.current.textSecondary, fontSize = 14.sp))
                }
                VanCollapseItem(name = "2", title = "标题二") {
                    androidx.compose.foundation.text.BasicText("第二块。", style = TextStyle(color = LocalVantColors.current.textSecondary, fontSize = 14.sp))
                }
            }
        }
    }
    Section("DropdownMenu 下拉菜单") {
        VanDropdownMenu(
            items = listOf(
                VanDropdownMenuItemData(title = d1, value = d1, options = listOf("全部", "进行中", "已完成").map { VanDropdownOption(it, it) }),
            ),
            onChange = { _, v -> d1 = v.toString() },
        )
    }
    Section("NavBar 导航栏") {
        VanNavBar(title = "页面标题", leftArrow = true, onClickLeft = {}, rightText = "更多", onClickRight = {})
    }
}

/* ============================= 其它 ============================= */

@Composable
private fun OtherPage() = Page {
    var cal by remember { mutableStateOf(false) }
    val toast = LocalVanToastController.current
    Section("Swipe 轮播") {
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            VanSwipe(count = 3, autoplay = 2500) { idx ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(listOf(Color(0xFF3A66E0), Color(0xFF07C160), Color(0xFFFF976A))[idx]),
                ) {
                    androidx.compose.foundation.text.BasicText("幻灯片 ${idx + 1}", style = TextStyle(color = Color.White, fontSize = 18.sp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(24.dp).background(Color.White), contentAlignment = Alignment.CenterStart) {
            VanSwipe(count = 2, autoplay = 2000, vertical = true, showIndicators = false) { idx ->
                androidx.compose.foundation.text.BasicText("纵向通知 ${idx + 1}", style = TextStyle(fontSize = 12.sp, color = LocalVantColors.current.textTertiary), modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
    Section("Calendar 日历") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            VanButton("打开日历（单选）", { cal = true }, type = VanButtonType.Primary, block = true)
        }
        if (cal) {
            VanCalendar(
                show = true,
                onClose = { cal = false },
                onConfirm = { days ->
                    cal = false
                    toast.success("已选: ${days.joinToString { it.toString() }}")
                },
            )
        }
    }
    Section("Row / Col 栅格") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            VanRow {
                VanCol(span = 8) { Box(Modifier.height(30.dp).background(Color(0xFFEAF0FD))) }
                VanCol(span = 8) { Box(Modifier.height(30.dp).background(Color(0xFFF0F9EB))) }
                VanCol(span = 8) { Box(Modifier.height(30.dp).background(Color(0xFFFFF7E8))) }
            }
            Spacer(Modifier.height(4.dp))
            VanRow {
                VanCol(span = 12) { Box(Modifier.height(30.dp).background(Color(0xFFEAF0FD))) }
                VanCol(span = 12) { Box(Modifier.height(30.dp).background(Color(0xFFF0F9EB))) }
            }
        }
    }
    Section("Uploader 上传") {
        Column(Modifier.background(Color.White).padding(12.dp)) {
            VanUploader(fileList = emptyList(), onPick = { toast.show("选了 ${it.size} 个文件") })
        }
    }
}
