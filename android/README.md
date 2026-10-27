# Lumen 观读 · Android

Kotlin + Jetpack Compose Material 3 的 Lumen Android 版。

## 已实现

- 精选推荐（沿用桌面版权重与质量信号）
- 各站前三、媒体与机构目录、来源开关（由导入的爬虫插件提供）
- 原站 WebView 阅读、划词、词典/翻译、收藏、阅读足迹
- 插件中心：应用不内置插件；与桌面共用官方 ZIP，支持 ZIP/声明式 JS 导入、启停与移除
- 点击文章直接进入阅读模式：后台加载原站、Readability 抽取正文、逐段翻译（微软官方网页 / Microsoft API / DeepL / MyMemory），原站排版按需打开
- 针对重点来源优化的去广告插件，在原文与精读中清理广告
- 词句本、间隔复习、收藏、足迹
- 桌面版学习备份导入、JSON/CSV 导出
- 22 套配色（12 套 legado 色板 + 10 套与桌面同名的主题）与「界面圆角」滑条，圆角设置与桌面端共用
- Material 3 动态取色、深浅色、字号与推荐权重设置
- 深色优先的衬线版式：大号页面标题、圆形图标按钮、近黑分组卡片、蓝色衬线小节标题、环形阅读目标仪表、封面横滚
- 液态玻璃（`io.github.kyant0:backdrop`）覆盖全部界面：卡片、按钮、页签、开关、滑块与悬浮底栏共用同一套玻璃表面，选中气泡会横向拉伸、纵向压扁。玻璃采样的是内容下方的壁纸层，底栏采样内容层——玻璃若采样自己所在的渲染层会形成循环引用并崩溃。默认开启，可在 设置 → 界面质感 关闭：Android 13+ 完整折射，Android 12 仅背景模糊，更低版本回退为半透明面板。
- 启动时自动更新（可选）

## 构建

需要 JDK 21、Android SDK 37、系统 Gradle（本工程未提交 wrapper）。

```sh
cd android
gradle assembleDebug     # 调试包
gradle assembleRelease   # 正式签名包
```

- 正式签名读取 `local.properties`（不入库）里的 `lumen.release.storeFile` / `keyAlias` / `storePassword` / `keyPassword`；缺省时 release 构建会退回未签名。密钥请自行备份，丢失后无法覆盖升级。
- 构建官方插件 ZIP：在仓库根目录运行 `go run ./cmd/package-plugins`，产物在 `dist/plugins/`。
- 产物：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`；发布用的是 `Lumen-Android-1.0.0.apk`（release 自签名）。

安装：

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n studio.lumen.reader/.MainActivity
```

> release 与 debug 签名不同，二者不能互相覆盖安装；从 debug 换到 release 需要先卸载。

## 许可

本目录以 **GNU GPLv3** 发布（见 `LICENSE` 与 `NOTICE`），因为其中的界面组件改编自 GPLv3 的 [legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)。仓库其余部分仍为 MIT。

## 界面截图

| 精选 | 阅读模式 | 学习页概览 |
| --- | --- | --- |
| ![精选](screenshots/home.png) | ![阅读模式](screenshots/reader.png) | ![概览](screenshots/study.png) |

| 各站前三 | 设置 | DeepSeek |
| --- | --- | --- |
| ![前三](screenshots/newsroom.png) | ![设置](screenshots/settings.png) | ![DeepSeek](screenshots/deepseek.png) |

| 插件中心 | 设置 |
| --- | --- |
| ![插件中心](screenshots/plugins.png) | ![设置](screenshots/settings.png) |

## 测试

```sh
gradle testDebugUnitTest
```

已覆盖 URL 安全、推荐时效、复习间隔和 CSV 注入；模拟器（API 36）已人工验收精选/阅读/划词/双语/设置/DeepSeek 登录页。

## 数据

数据保存在应用私有目录 `library.json`；用户插件在 `plugins/<id>`，APK 只携带去广告运行引擎。首次启动从 `assets` 的桌面快照种子填充；设置中包含导入桌面版 `lumen-backup` JSON 的入口。

## 与桌面版的差异

- 应用不内置来源插件；首次使用需在插件中心导入 `dist/plugins/lumen-crawler-*.zip`，否则媒体目录与各站前三为空，可从空状态直接进入插件中心。
- 默认翻译为「微软网页」（Edge 内置的免密钥接口，与桌面端同一条通道）；该接口非公开、可能限流，可改用微软 API / DeepL / MyMemory。
- 1.1.0-beta.9 起不再内置 ChatGPT / DeepSeek 网页助手；阅读提问只在桌面端的观读轻译小窗里进行。
- 轻量存储仅停止持久化文章/译文/各站快照；WebView 登录数据不会切换临时档案。
- 安装包为 release 自签名 APK（`CN=Lumen Reader`），不是 Google Play 的上架签名；未开启代码裁剪，因为 WebView 的 `@JavascriptInterface` 桥接还缺少经过验证的 R8 keep 规则。
