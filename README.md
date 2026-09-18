# vant-android — web 项目 Vant 组件的安卓原生 1:1 复刻

> **声明**：`vant-ui` 为本项目内部自研 Compose 组件库（独立实现，不含 Vant 源码），
> 与有赞/Vant 官方无隶属关系；图标字体取自 MIT 协议的 Vant 4.9.22。详见
> [vant-ui/README.md](vant-ui/README.md)。

本模块把 `web/`（Vue3 + Vant 4.9.22）中实际用到的 **45 类 Vant 组件与 7 类函数式 API**，
用 Jetpack Compose 一比一复刻为安卓原生组件，供重构原生 APP 时直接调用。

> 组件盘点与使用统计见 [docs/vant-components-inventory.md](docs/vant-components-inventory.md)。
>
> **接手/改造请先读 [docs/项目构造书.md](docs/项目构造书.md)**：构造书含「文件↔源文件对照表 / 改造手册 / 注意事项 / P0–P3 坑清单」。

## 工程结构

```
APP/
├── settings.gradle.kts        # 两个模块
├── vant-ui/                   # 组件库（复刻本体）
│   └── src/main/
│       ├── res/font/vant_icon.ttf   # Vant 原版图标字体（从 vant/lib/index.css 内嵌 base64 提取转 TTF）
│       └── java/com/live/vant/
│           ├── theme/    # VantColors / VantDimens / VantTheme（项目 CSS 变量 1:1）
│           ├── icon/     # VanIcon + VantIconCodes（259 个图标码点）
│           ├── basic/    # Button Cell CellGroup Loading Overlay Divider Tag Progress
│           │             # Circle NoticeBar Skeleton Image Empty
│           ├── form/     # Field Stepper Switch Radio Checkbox Picker DatePicker
│           │             # NumberKeyboard PasswordInput Uploader Search
│           ├── feedback/ # Popup Toast Dialog ActionSheet ImagePreview PullRefresh List
│           ├── nav/      # NavBar Tabs Tabbar DropdownMenu Collapse
│           ├── layout/   # Row / Col（24 栅格）
│           └── other/    # Swipe Calendar
└── demo/                      # 全部组件的可视化演示 App（5 页，本身即 van-tabbar 复刻）
```

## 构建与运行

环境：JDK 17、Android SDK（compileSdk 34）、AGP 8.5.2、Kotlin 1.9.24、Compose BOM 2024.06.00。

```bash
# 方式一：Android Studio 打开 APP/ 目录，Sync 后运行 :demo
# 方式二：命令行（需先配置 ANDROID_HOME 或 local.properties）
cd APP && ./gradlew :demo:assembleDebug
```

> 说明：本仓库所在机器未安装 Android SDK，未执行 assembleDebug；但全部 44 个源文件已通过
> **Kotlin 1.9.24 embeddable + Compose Compiler 1.5.14 插件**对 Compose 1.6 真实 API 的
> 全量类型检查与字节码生成（0 错误，509 个 class）。版本矩阵（AGP 8.5.2 + Kotlin 1.9.24 +
> Compose BOM 2024.06.00，compileSdk 34 / minSdk 24）导入 Android Studio 即可构建。
> 验证工程（编译器、桌面 JAR、Coil/activity 桩）保留在仓库 `temp/libs`、`temp/stubs`，
> 命令模板见 `temp/kcheckI.log` 头部，后续改库可复跑。

## 主题（复刻的是项目实际生效主题）

`web/src/assets/css/style.css` 用 `--van-*` 覆盖了 Vant 出厂主题，本库的默认主题 = 覆盖后的结果：
primary **#3a66e0**、文字 #323233/#646566/#969799、页面底 #f7f8fa、卡片 #fff、边框 #ebedf0 等。

```kotlin
// App 根部包一层即可
VantTheme {                       // 浅色（项目主题）
    VantTheme(VantDarkColors.dark()) { ... }   // 深色（对应 data-theme-mode=dark）
    // 需要 Vant 出厂蓝时：VantTheme(VantColors.classic())
}
```

## 图标（像素级 1:1 的关键）

`van-icon` 的 413 处使用全部来自同一套字体。`vant_icon.ttf` 直接提取自 `vant/lib/index.css`
内嵌的 `@font-face vant-icon`（woff2 → fontTools 转 TTF），259 个名称→码点映射自动生成于
`VantIconCodes.kt`。因此任意图标名（arrow、cross、location-o、credit-pay…）渲染结果与 web 完全一致。

再生成映射表（修改 vant 版本后执行，需 `pip install fonttools brotli`）：

```python
import re, base64, io, json
css = open('web/node_modules/vant/lib/index.css', encoding='utf-8').read()

# 1) 字体：内嵌 woff2 → TTF
m = re.search(r'font-family:vant-icon;[^}]*base64,([A-Za-z0-9+/=]+)', css)
from fontTools.ttLib import TTFont
font = TTFont(io.BytesIO(base64.b64decode(m.group(1)))); font.flavor = None
font.save('APP/vant-ui/src/main/res/font/vant_icon.ttf')

# 2) 码点表 → temp/vant_icon_map.json
mapping = {}
for mm in re.finditer(r'\.van-icon-([a-z0-9-]+):before\{content:"', css):
    s = mm.end(); e = css.index('"', s)
    mapping[mm.group(1)] = int(css[s:e].lstrip(chr(92)), 16)
json.dump(mapping, open('temp/vant_icon_map.json', 'w'), separators=(',', ':'))

# 3) JSON → Kotlin（把映射值以真实 PUA 字符嵌入 '…' 字面量，生成
#    APP/vant-ui/src/main/java/com/live/vant/icon/VantIconCodes.kt，见 VantIconCodes 文件头注释）
```

## Vant → 安卓 API 映射（迁移时按此表替换）

| Vant | Compose | 关键 props/事件 |
|------|---------|----------------|
| `<van-button type size block plain round loading disabled @click>` | `VanButton(text, onClick, type=, size=, block=, plain=, round=, loading=, disabled=)` | 枚举 `VanButtonType/Size` |
| `<van-cell title value label icon is-link center @click>` | `VanCell(title=, value=, label=, icon=, isLink=, center=, onClick=, …Slot)` | 插槽 → 尾随/具名 composable |
| `<van-cell-group title inset>` | `VanCellGroup(title=, inset=) { … }` | |
| `<van-icon name size color @click :badge>` | `VanIcon(name, size=, color=, badge=, dot=, onClick=)` | 图像型 name（URL）自动切 Coil |
| `<van-field v-model label placeholder type readonly clearable>` | `VanField(value, onValueChange, label=, placeholder=, type=, readonly=, clearable=)` | |
| `<van-picker columns title @confirm @cancel>` | `VanPicker(columns=, onConfirm=, onCancel=, title=)` | |
| `<van-date-picker v-model min-date max-date type>` | `VanDatePicker(type=, value=, minDate=, maxDate=, onConfirm=)` | LocalDate |
| `<van-switch v-model size active-color>` | `VanSwitch(checked, onCheckedChange, size=, activeColor=)` | |
| `<van-stepper v-model min max>` | `VanStepper(value, onValueChange, min=, max=)` | |
| `<van-radio-group v-model direction>` / `van-radio` | `VanRadioGroup(value, onValueChange) { VanRadio(name=, label=) }` | |
| `<van-checkbox-group v-model>` / `van-checkbox` | `VanCheckboxGroup(value, onValueChange) { VanCheckbox(name=) }` | |
| `<van-number-keyboard v-model show theme extra-key close-button-text @input @delete @close>` | `VanNumberKeyboard(show, text, onTextChange, theme=, extraKey=, onClose=)` | |
| `<van-password-input value length focused gutter>` | `VanPasswordInput(value, length=, focused=, gutter=)` | |
| `<van-uploader v-model max-count multiple @delete after-read>` | `VanUploader(fileList, onPick=, maxCount=, onDelete=)` | after-read 上传 = onPick 回调接业务 |
| `<van-search v-model placeholder show-action @search @cancel @clear>` | `VanSearch(value, onValueChange, placeholder=, showAction=, onSearch=, onCancel=, onClear=)` | |
| `<van-popup v-model:show position round>` | `VanPopup(show, onDismissRequest, position=, round=)` | |
| `<van-overlay show z-index @click>` | `VanOverlay(show, onClick=)` | |
| `<van-loading size type color vertical>` | `VanLoading(size=, type=, color=, vertical=)` | |
| `<van-toast>` 系列 | `LocalVanToastController.current.show/success/fail/loading/close` | 根部挂 `VanToastHost` |
| `showConfirmDialog({title,message})` | `VanConfirmDialog(show, title=, message=, onConfirm=, onCancel=)` | |
| `showDialog` | `VanAlertDialog` / `VanDialog` | |
| `<van-action-sheet actions title cancel-text @select>` | `VanActionSheet(show, onDismissRequest, actions=, onSelect=)` | |
| `<van-image-preview images closeable @change>` | `VanImagePreview(show, images=, closeable=, onChange=, onClose=)` | |
| `<van-pull-refresh v-model @refresh success-text>` | `VanPullRefresh(state, onRefresh, successText=)` + `state.finishRefresh()` | |
| `<van-list v-model:loading finished finished-text @load>` | `VanList(state=, loading=, finished=, onLoad=)` + `VanListFooter(...)` | |
| `<van-nav-bar title left-arrow @click-left @click-right>` | `VanNavBar(title=, leftArrow=, onClickLeft=, onClickRight=)` | |
| `<van-tabs v-model:active><van-tab title name>` | `VanTabs(active, onActiveChange, tabs=) { name -> }` | |
| `<van-swipe :autoplay :loop vertical show-indicators>` | `VanSwipe(count=, autoplay=, loop=, vertical=)` | |
| `<van-collapse v-model><van-collapse-item name title icon>` | `VanCollapse(activeNames, onActiveNamesChange) { VanCollapseItem(name, title) }` | |
| `<van-notice-bar text left-icon mode scrollable>` | `VanNoticeBar(text, leftIcon=, mode=)` | |
| `<van-calendar v-model:show type min-date max-date @confirm>` | `VanCalendar(show, onClose, type=, minDate=, maxDate=, onConfirm=)` | |
| `<van-skeleton row title loading>` | `VanSkeleton(loading, row=, title=) { 内容 }` | |
| `<van-empty description image>` | `VanEmpty(description=, image=)` | 内置插画含 default/error/search/network |
| `<van-tag type plain round closeable>` | `VanTag(text, type=, plain=, round=, closeable=)` | |
| `<van-progress percentage stroke-width color show-pivot>` | `VanProgress(percentage, strokeWidth=, color=)` | |
| `<van-circle rate text>` | `VanCircle(rate, text=, size=)` | |
| `<van-divider content-position dashed>` | `VanDivider(content=, dashed=, contentPosition=)` | |
| `<van-row gutter><van-col span>` | `VanRow(gutter=) { VanCol(span=) }` | |

## 已知实现差异（如实声明）

1. **点击态**：Vant 用 CSS `:active` 变色，本组件用等价的按压叠色（无 Material 水波纹）——视觉 1:1；
   如团队要求水波纹可另加 `ripple()`。
2. **Popup / ActionSheet / Calendar / NumberKeyboard / ImagePreview** 走独立 Window（Dialog）实现，
   天然盖全屏；`z-index` 语义由层级顺序表达。
3. **Toast** 无 Vue 的全局命令式调用，采用 `VanToastController` 状态宿主（见上表），
   行为（duration/forbidClick/loading 不自动关）与 Vant 一致。
4. **DropdownMenu** 的全屏灰遮罩简化为内联展开 + 点选收起；菜单栏/选项样式 1:1。
5. **Lazyload**：Coil 按可见性加载，语义覆盖 `lazy-load`；网络权限由调用方声明。
6. **van-uploader** 的 `before-read/after-read`（压缩+上传链路）属于业务逻辑：
   `onPick(List<Uri>)` 回调交业务处理（对应 web 里 browser-image-compression + 上传后写回 fileList）。
7. 空状态 `albums-o / computer-o / palette` 三个图标名在 Vant 4.9.22 字体中本就不存在，
   两端同样渲染为空，非复刻缺失。
