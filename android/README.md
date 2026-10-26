# Lumen 观读 · Android

Kotlin + Jetpack Compose Material 3 的 Lumen Android 版。

## 已实现

- 精选推荐（沿用桌面版权重与质量信号）
- 各站前三、媒体与机构目录、来源开关（由导入的爬虫插件提供）
- 原站 WebView 阅读、划词、词典/翻译、收藏、阅读足迹
- 插件中心：应用不内置插件；与桌面共用官方 ZIP，支持 ZIP/声明式 JS 导入、启停与移除
- 打开文章后自动进入双语精读；Readability 逐段翻译（微软官方网页 / Microsoft API / DeepL / MyMemory）
- 针对重点来源优化的去广告插件，在原文与精读中清理广告
- 词句本、间隔复习、收藏、足迹
- 内置 ChatGPT / DeepSeek 网页（WebView，需登录）
- 桌面版学习备份导入、JSON/CSV 导出
- Material 3 动态取色、深浅色、字号与推荐权重设置
- 液态玻璃底栏（`io.github.kyant0:backdrop`）与可选悬浮底栏，默认关闭。开启后正文从底栏下方滚过，玻璃会实时采样并折射背景：Android 13+ 完整折射，Android 12 仅背景模糊，更低版本回退为带色调的半透明面板。
- 启动时自动更新（可选）

## 构建

需要 JDK 21、Android SDK 36、系统 Gradle（本工程未提交 wrapper）。

```sh
cd android
gradle assembleDebug
```

- 构建官方插件 ZIP：在仓库根目录运行 `go run ./cmd/package-plugins`，产物在 `dist/plugins/`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`；仓库内已附预构建的 [`Lumen-Android-1.0.0-debug.apk`](Lumen-Android-1.0.0-debug.apk)（debug key 签名）。

模拟器安装：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n studio.lumen.reader/.MainActivity
```

## 界面截图

| 精选 | 阅读器 | 双语精读 |
| --- | --- | --- |
| ![精选](screenshots/home.png) | ![阅读](screenshots/reader.png) | ![双语](screenshots/bilingual.png) |

| 各站前三 | 设置 | DeepSeek |
| --- | --- | --- |
| ![前三](screenshots/newsroom.png) | ![设置](screenshots/settings.png) | ![DeepSeek](screenshots/deepseek.png) |

| 插件中心 | 自动双语 |
| --- | --- |
| ![插件中心](screenshots/plugins.png) | ![自动双语](screenshots/bilingual.png) |

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
- ChatGPT/DeepSeek 是内置网页版，需要自行登录且受网络可达性影响。
- 轻量存储仅停止持久化文章/译文/各站快照；WebView 登录数据不会切换临时档案。
- 安装包为 debug APK（debug key 签名），不是商店发布签名。
