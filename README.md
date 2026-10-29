# Lumen · 观读

一款面向英语学习的阅读应用：连接英文媒体、大学和研究机构，在原文阅读中积累词汇、理解句子。

桌面版使用 **Wails v2 + 系统 WebView2** 重写，安装包不再携带 Chromium。当前版本 **1.1.1**。

## 来源与致谢

本项目延续自上游 [sadinoel0106/Lumen](https://github.com/sadinoel0106/Lumen)（MIT License，原始 Electron 版 1.0.6），并已获得原作者授权继续开发。产品概念、界面文案、推荐与排序规则、学习功能设计均沿自上游；1.0.0 指本次 Wails + Android 重写后的首个版本，与上游的 1.0.6 是两条版本号线索。上游版权与许可证保留在 [LICENSE](LICENSE) 与 [第三方声明](THIRD_PARTY_NOTICES.md) 中。

## 下载

项目主页：<https://shenliuyu18.github.io/lumen/>（两端安装包与官方插件的下载入口、功能介绍与许可说明）。

[Releases](https://github.com/shenliuyu18/lumen/releases) 页面提供：

| 文件 | 说明 |
| --- | --- |
| `Lumen-Setup-1.1.1.exe` | Windows x64 按用户安装（不需要管理员权限），未做代码签名 |
| `Lumen-Android-1.1.1.apk` | Android 8.0+（minSdk 26） |
| `lumen-crawler-1.1.1.zip`、`lumen-adblock-1.1.1.zip`、`lumen-paywall-1.1.1.zip` | 官方插件，安装应用后在插件中心导入 |

安装包未签名，Windows SmartScreen 会提示「未知发布者」，选择「仍要运行」即可。应用不内置插件，首次启动后请先导入上面三个插件 ZIP。

## 可以做什么

- **综合推荐**：结合时效、来源影响力、跨来源同题覆盖、前沿性与学习信号，展示可查看依据的推荐。评分是规则分析，不代表实测流量或全文事实核查。
- **各站前三**：汇总 123 个英文媒体与机构，区分首页候选与订阅顺序，并显示更新时间。
- **应用内原文阅读**：嵌入的 WebView2 阅读视图打开原站，保留登录态；支持后退、前进、刷新、缩放、外部打开与划词。
- **阅读模式**：默认点击文章先打开原站页面，点顶栏「阅读模式」（Android 为原站页的书本图标或底部「阅读模式」按钮）才在后台加载并抽取正文；偏好设置里的「自动进入阅读模式」开关（默认关闭）可以实现点文章直接精读。阅读模式顶栏保留双语、稍后阅读、提问、重新抽取与原站入口，Android 默认只显示原文，点「双语」后逐段翻译。过滤广告、导航等独立区域，把译文放在英文段落下方。
- **划词与翻译**：查词、句段翻译；默认用微软官方网页翻译（免密钥，即 Edge 内置的翻译服务），可改用 Microsoft Translator、DeepL、OpenAI 兼容的大模型（地址、模型与密钥都可自定义）或 MyMemory。
- **词句本与复习**：保存词句、间隔复习、收藏文章，支持学习资料 JSON 备份与 CSV 导出。
- **阅读时提问**：阅读页右下角的「问」悬浮球打开提问小窗，可附带选文或本文节选、连续追问、停止回答、复制回答，回答按 Markdown 与 emoji 排版；窗口可左右停靠、宽 320–900px、高 360–1000px。问答沿用「翻译服务」的接口地址、模型与密钥，只选「大模型 · OpenAI 兼容」时可提问，不再单独保存一套 Key。
- **译文显示设置**：双语段落支持排版、底色与边框、8 套配色、8 种字体、字重、字号、行距、对齐、留白、字距、首行缩进与圆角，附「经典暖纸 / 雾蓝轻读 / 宋体书页 / 重点加粗」四个预设与实时预览；只重排译文块，不改原站页面。
- **插件中心**：Lumen 不内置来源或广告规则。爬虫与去广告插件独立打包，桌面与安卓共用；支持导入 ZIP 或声明式 JS、启停与移除。去广告插件针对 Lumen 目录中的重点来源优化，在原文与精读中清理广告。
- **十款主题、圆角与缓存管理**：浅色配色、阅读足迹、缓存清理与轻量存储模式；「界面圆角」滑条（0–28）统一桌面与安卓的卡片和按钮圆角。
- **界面质感**：Android 端卡片、按钮、页签、滑块与底栏共用一套液态玻璃表面；桌面端在 设置 → 界面质感 可开启同样的玻璃主题。两者都可在设置里关掉。Android 13+ 有完整折射，Android 12 只有背景模糊，更低版本回退为半透明面板。

## Android 版

原生 Kotlin + Jetpack Compose Material 3 版本位于 [`android/`](android/README.md)，构建说明和已知差异见该目录 README。

## 插件

应用安装包不内置插件。官方插件作为独立 ZIP 放在 [`dist/plugins/`](dist/plugins/)：

- `lumen-crawler-1.1.1.zip`：123 个来源的 RSS、目录与「各站前三」解析规则。
- `lumen-adblock-1.1.1.zip`：通用广告选择器、广告标签文本规则，以及约 18.5 万个广告域名的请求拦截规则（桌面 WebView2 与安卓 WebView 都直接拦截，主文档请求永远放行）。
- `lumen-paywall-1.1.1.zip`：从 bypass-paywalls-chrome-clean 提取的付费墙脚本域名与遮挡层选择器，只清理页面元素，不抓取存档、不改正文。

首次使用时在插件中心（桌面侧栏「插件中心」/ Android 设置 → 插件）导入这三个 ZIP。也可以导入自制插件：

- **ZIP**：根目录或一级目录内包含 `plugin.json`，`type` 为 `crawler` 或 `adblock`。
- **JS**：使用 `module.exports = {...}`、`export default {...}` 或 `window.__LUMEN_PLUGIN__ = {...}` 导出与 `plugin.json` 相同的 JSON 对象。Lumen 只解析对象，不执行脚本内容。

插件可启停、可移除；爬虫插件停用或未安装时，媒体目录、各站前三与订阅更新不可用。插件保存在 `%APPDATA%\Lumen\plugins\<id>`（Android 为应用私有目录 `plugins/<id>`）。

## 环境

- Windows x64，系统已安装 WebView2 Runtime（Windows 11 通常自带）。
- Go 1.24+。
- Wails CLI v2.16。
- NSIS（仅在生成安装包时需要）。

## 构建与测试

```sh
go test ./...
go run ./cmd/package-plugins   # 生成 dist/plugins/*.zip
wails build -platform windows/amd64
```

生成安装包（按用户安装，无需管理员权限）：

```sh
wails build -platform windows/amd64 -nsis -installscope user -webview2 download
```

产物：

- `build/bin/Lumen.exe`：可执行程序。
- `build/bin/Lumen-amd64-installer.exe`：NSIS 安装包，发布时命名为 `Lumen-Setup-1.0.0.exe`。
- `dist/plugins/*.zip`：与安装包分开分发的官方插件包，不进入 exe/APK。

安装包未配置代码签名。Windows x64 为当前验收平台。

## 数据与迁移

- 资料库：`%APPDATA%\Lumen\library.json`。
- 网站登录与缓存：`%APPDATA%\Lumen\webview2\<ui|reader|extractor>`；旧版 ChatGPT / DeepSeek / 翻译面板留下的档案目录会在清理缓存时一并处理。
- 用户插件：`%APPDATA%\Lumen\plugins\<id>`；应用只携带去广告运行引擎，不携带插件清单。
- 首次启动会检测 Electron 版的 `%APPDATA%\lumen-reader\library.json`，备份后导入文章、设置、词句、收藏和足迹。
- Electron 使用系统能力加密的翻译 API 密钥无法跨运行时迁移，需要在偏好设置中重新录入；新密钥使用 Windows DPAPI 保存，不进入备份。
- 轻量存储模式使用临时网站资料目录，关闭应用后丢弃；该设置重启后生效。
- 启动时会清理各 WebView2 档案的组件、GPU 与 HTTP 缓存（保留 Cookie 与登录态），并限制子视图缓存上限。旧 Electron 版的 `%APPDATA%\lumen-reader` 缓存不会自动删除。
- 阅读提问与翻译共用一套接口配置：在偏好设置里选「大模型 · OpenAI 兼容」，填接口地址、模型与密钥（DeepSeek 官方为 `https://api.deepseek.com/v1` + `deepseek-chat`），密钥使用 DPAPI 保存且不进入备份。1.1.0 起不再有内嵌的 ChatGPT / DeepSeek 网页面板。

## 结构

| 路径 | 用途 |
| --- | --- |
| `app.go` / `main.go` | Wails 入口、绑定与事件 |
| `internal/webhost` | 自绘 WebView2 宿主：reader / chat / translator / 独立窗口 |
| `internal/service` | 推荐、刷新、翻译、学习资料与存储用例 |
| `internal/feed` / `newsroom` / `rank` | RSS、各站前三、推荐排序 |
| `internal/extractor` | 页面内 Readability 提取脚本与资产 |
| `internal/access` | 公开性核验与逐篇翻译授权 |
| `internal/plugins` | 插件发现、启停、ZIP/JS 安装与去广告脚本合并 |
| `plugins/` | 官方插件源文件（不嵌入应用）与共享去广告运行引擎 |
| `dist/plugins/` | 可独立分发的官方插件 ZIP |
| `ui/` | 本地界面、十套主题与 Wails bridge |
| `docs/` | 开发说明与设计记录 |

## 安全与边界

- 远程网页只通过白名单消息协议与 Go 通信，不获得 Wails/Go 绑定。
- 阅读模式会在开始翻译前核对匿名可读性并逐段翻译；授权绑定文章、提供方与段落语境，只发送当前提取正文。
- 插件只包含声明式来源与规则，不执行插件携带的代码；ZIP/JS 安装会校验路径、ID 与解压体积，插件可随时移除。
- 去广告规则只操作页面 DOM，不修改正文，也不绕过登录或付费墙。
- 网站权限与下载默认拒绝，外部链接交给系统浏览器。
- 推荐的依据与快照范围如实展示，不把启发式评分当作流量或事实核查。

## 许可证

本仓库沿用上游的 [MIT 许可证](LICENSE)，版权归属以 `LICENSE` 中的上游作者为准；本项目的衍生修改同样以 MIT 发布。第三方源码保留各自许可证，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。媒体内容、商标与外部服务的权利由各自所有者保留。
