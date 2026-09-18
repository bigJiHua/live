# vant-ui — 项目内部自研 Compose 组件库

`vant-ui` 是本项目（安卓原生 APP）的**内部 UI 组件库**，使用 Jetpack Compose 独立编写，
提供与项目 web 端（Vue3 + Vant 4.9.22）一致的外观与交互行为，供 `:app` 业务层直接调用。

## 声明与第三方素材（避嫌说明）

- 本模块为**独立实现**：全部 Compose 代码为本项目原创编写，**不包含、未移植 Vant 的任何
  JS / CSS / Vue 源码**，仅参照其公开文档实现视觉与交互效果，用于和本项目 web 端保持一致。
- 本模块与有赞（YouZan）/ Vant 官方**无任何隶属或合作关系**，非官方库，**不对外发布、不分发**。
- 组件命名（`Van*` 前缀）仅用于项目内部与 web 端组件名的对照映射，便于维护，别无他意。
- 唯一使用的第三方素材：`src/main/res/font/vant_icon.ttf` 图标字体，提取自
  [Vant 4.9.22](https://github.com/youzan/vant)（MIT License，Copyright (c) YouZan），
  依 MIT 协议条款使用并在此致谢与声明。
- 若计划将本仓库公开或商用，请自行复核第三方素材的许可条款。

## 组件清单（45 类组件 + 7 类函数式 API）

| 分组 | 组件 |
| --- | --- |
| theme | VantColors / VantDimens / VantTheme（项目 CSS 变量 1:1 映射） |
| icon | VanIcon + VantIconCodes（259 个图标码点，字体渲染） |
| basic | Button Cell CellGroup Loading Overlay Divider Tag Progress Circle NoticeBar Skeleton Image Empty |
| form | Field Stepper Switch Radio Checkbox Picker DatePicker NumberKeyboard PasswordInput Uploader Search |
| feedback | Popup Toast Dialog ActionSheet ImagePreview PullRefresh List |
| nav | NavBar Tabs Tabbar DropdownMenu Collapse |
| layout | Row / Col（24 栅格） |
| other | Swipe Calendar |

组件盘点与各组件在业务侧的使用统计见 [docs/vant-components-inventory.md](../docs/vant-components-inventory.md)。

## 构建与验证

版本矩阵：AGP 8.5.2 + Kotlin 1.9.24 + Compose BOM 2024.06.00（compileSdk 34 / minSdk 24），
导入 Android Studio 打开 `APP/` 即可随工程构建；`:demo` 模块提供全部组件的可视化演示页。
