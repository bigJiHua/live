# Vant 组件清单（web 项目扫描结果）

> 扫描范围：`web/src/**/*.vue`、`web/src/**/*.js`（Vant 4.9.22，`main.js` 全量引入）。
> 统计口径：标签使用次数（`<van-x>`）与函数式 API 调用次数（`showToast` 等）。
> 日期：2026-09-14。

## 一、组件使用排行（45 类组件）

| # | Vant 组件 | 使用次数 | 项目实际用到的 props / slots / events | 安卓复刻 |
|---|-----------|---------|----------------------------------------|---------|
| 1 | Icon | 413 | name / :name / size / :color / class / @click | `VanIcon`（字体 1:1，259 码点） |
| 2 | CellGroup | 125 | title / inset / border | `VanCellGroup` |
| 3 | Empty | 64 | description / image / slots(icon,image) | `VanEmpty`（4 套内置插画） |
| 4 | Picker | 54 | columns / title / v-model / show-toolbar / confirm / cancel | `VanPicker` |
| 5 | Loading | 46 | size / color / type / vertical | `VanLoading` |
| 6 | Radio | 28 | name / shape / disabled | `VanRadio` |
| 7 | Tab | 25 | title / name | `VanTabItem`（并入 `VanTabs`） |
| 8 | Switch | 24 | v-model / size / active-color | `VanSwitch` |
| 9 | Image | 22 | src / fit / width / height / radius / round / lazy-load / @click | `VanImage` |
| 10 | DatePicker | 17 | type / title / v-model / min-date / max-date / confirm / cancel | `VanDatePicker` |
| 11 | Overlay | 14 | show / z-index / @click | `VanOverlay` |
| 12 | Divider | 13 | content-position / dashed / style | `VanDivider` |
| 13 | Button | 12 | type / size / block / plain / round / disabled / @click | `VanButton` |
| 14 | RadioGroup | 11 | v-model / direction | `VanRadioGroup` |
| 15 | Cell | 10 | title / value / label / icon / is-link / center / slots | `VanCell` |
| 16 | Stepper | 9 | v-model / min / max | `VanStepper` |
| 17 | PullRefresh | 9 | v-model / success-text / disabled / @refresh | `VanPullRefresh` |
| 18 | NumberKeyboard | 9 | theme / v-model / show / maxlength / extra-key / close-button-text / blur / input / delete / close | `VanNumberKeyboard` |
| 19 | Field | 9 | v-model / label / placeholder / type / readonly / input-align / @click | `VanField` |
| 20 | Uploader | 8 | v-model / max-count / after-read / before-read / accept / multiple / preview-size / @delete | `VanUploader` |
| 21 | Tabs | 8 | v-model:active / sticky / shrink / animated / swipeable / @change | `VanTabs` |
| 22 | Search | 8 | v-model / placeholder / show-action / shape / background / clearable / @search @cancel @clear | `VanSearch` |
| 23 | Popup | 8 | v-model:show / position / round / style | `VanPopup` |
| 24 | Collapse | 8 | v-model / border | `VanCollapse` |
| 25 | CollapseItem | 8 | name / title / icon / border / slots | `VanCollapseItem` |
| 26 | Col | 8 | span / @click | `VanCol` |
| 27 | NoticeBar | 6 | text / left-icon / scrollable / wrapable / color / background / mode | `VanNoticeBar` |
| 28 | Calendar | 6 | v-model:show / type / min-date / max-date / color / @confirm | `VanCalendar` |
| 29 | Skeleton | 5 | row / title / loading | `VanSkeleton` |
| 30 | PasswordInput | 5 | value / length / focused / gutter | `VanPasswordInput` |
| 31 | List | 5 | v-model:loading / finished / finished-text / immediate-check / @load | `VanList` + `VanListFooter` |
| 32 | ActionSheet | 5 | v-model:show / actions / title / cancel-text / @select | `VanActionSheet` |
| 33 | TabbarItem | 4 | icon | `VanTabbarItemData` |
| 34 | SwipeItem | 4 | — | `VanSwipe`（按 index 渲染） |
| 35 | Swipe | 4 | autoplay / vertical / touchable / loop / show-indicators / indicator-color | `VanSwipe` |
| 36 | Progress | 4 | percentage / stroke-width / color / show-pivot | `VanProgress` |
| 37 | NavBar | 4 | title / left-arrow / fixed / placeholder / click-left / click-right / slots | `VanNavBar` |
| 38 | Checkbox | 4 | v-model / name / shape / disabled | `VanCheckbox` |
| 39 | Tag | 3 | type / plain / size | `VanTag` |
| 40 | Row | 3 | gutter | `VanRow` |
| 41 | ImagePreview | 3 | v-model:show / images / start-position / closeable / @change | `VanImagePreview` |
| 42 | DropdownItem | 2 | v-model / options | `VanDropdownMenuItemData` |
| 43 | CheckboxGroup | 2 | v-model / shape | `VanCheckboxGroup` |
| 44 | Tabbar | 1 | v-model / fixed | `VanTabbar` |
| 45 | DropdownMenu | 1 | — | `VanDropdownMenu` |
| 46 | DatetimePicker | 1 | v-model / type / title / confirm / cancel | `VanDatetimePicker` |
| 47 | Circle | 1 | rate / text | `VanCircle` |

## 二、函数式 API（命令式调用）

| Vant API | 调用次数 | 安卓对应 |
|----------|---------|---------|
| `showToast` | 69 | `LocalVanToastController.current.show(...)` |
| `showConfirmDialog` | 28 | `VanConfirmDialog(show = ...)`（状态提升） |
| `showSuccessToast` | 19 | `.success(...)` |
| `showLoadingToast` | 8 | `.loading(...)` |
| `closeToast` | 8 | `.close()` |
| `showFailToast` | 4 | `.fail(...)` |
| `showDialog` | 2 | `VanAlertDialog` / `VanDialog` |
| `Lazyload` 插件 | 全局 | Coil 天然按需加载（`VanImage`） |
| `Icon` 组件 | 1 | `VanIcon` |

## 三、主题 Token（style.css 覆盖，复刻默认值即此套）

- `--van-primary-color`: **#3a66e0**（项目主色；Vant 出厂 #1989fa）
- success #07c160 / warning #ff976a / danger #ee0a24 / info #969799
- 文字：#323233 / #646566 / #969799 / 占位 #c8c9cc
- 背景：页面 #f7f8fa / 卡片 #ffffff / 三级 #f2f3f5
- 边框 #ebedf0；nav-bar 标题与图标色 #323233、文字按钮 #3a66e0；tabbar 激活 #3a66e0；
  switch 开启 #3a66e0；tag default 底 #f2f3f5 文字 #646566；
  picker 蒙层渐变使用页面底色 #f7f8fa；number-keyboard 面板 #fff / 按键 #f7f8fa / 完成键主色。

完整对照见 `APP/README.md`。

## 四、已知缺口（web 端即为空图标）

`albums-o`、`computer-o`、`palette` 三个名称在 Vant 4.9.22 图标库中不存在，
web 端本就渲染为空；安卓端 `VanIcon` 对未知名称同样只占位不出字形，行为一致。
