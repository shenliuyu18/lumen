# 开发、测试与构建

## 环境

- Go 1.24 或更高版本。
- Wails CLI v2.16。
- Windows x64 + WebView2 Runtime。
- NSIS（可选，用于安装包）。

```sh
go test ./...
wails build -platform windows/amd64
```

首次构建由 Wails 生成 `ui/wailsjs/`（已忽略提交）。前端为现有原生 HTML/CSS/JS，无 Node 构建步骤。

## 测试

```sh
go test ./...
```

覆盖：

- 来源目录、插件合并与停用、排序、去重、质量信号、复习间隔、CSV 注入防护。
- 插件 ZIP 安装的路径穿越、ID 与体积校验。
- 公开正文核验与逐篇授权规则。
- RSS 解析、首页头条筛选、stale 快照。
- 翻译请求与响应校验、缓存键。
- 资料库原子读写、低存储投影、损坏恢复、Electron 数据迁移。
- 全新安装的目录种子。

Windows 集成验收需手动运行应用，检查真实原站、划词、精读、翻译窗口、ChatGPT 侧栏与安装包。

## 代码结构

| 路径 | 职责 |
| --- | --- |
| `app.go` | Wails 生命周期、绑定与事件转发；webhost 与 service 的组合 |
| `internal/webhost` | Win32/WebView2 宿主。主窗口内使用子 HWND；翻译与 ChatGPT 小窗使用 owned top-level 窗口 |
| `internal/service` | 业务用例：状态、刷新、翻译缓存、DeepSeek 问答、词句本、收藏、足迹、备份与存储 |
| `internal/store` | `library.json` 原子写入、低存储投影、旧数据迁移 |
| `internal/feed` | HTTP + gofeed 解析、normalize、并发刷新 |
| `internal/newsroom` | goquery 抓首页、feed 回退、stale |
| `internal/rank` | 质量信号、加权排序、多样性、复习与 CSV（纯函数） |
| `internal/translate` | 微软官方网页 / Microsoft / DeepL / MyMemory 请求与响应 |
| `internal/access` | 匿名抓取核验、授权 token、段落语境校验 |
| `internal/extractor` | Readability.js + 提取包装脚本，嵌入二进制 |
| `internal/plugins` | 插件发现、ZIP/JS 安装、来源与各站前三规则、去广告脚本合并 |
| `internal/secrets` | DPAPI 加密保存翻译密钥 |
| `plugins/` | 官方插件源文件（不嵌入应用）与 `runtime/ads-engine.js` 共享去广告引擎 |
| `dist/plugins/` | 由 `go run ./cmd/package-plugins` 生成的官方插件 ZIP |
| `cmd/package-plugins/` | 将 `plugins/<id>/plugin.json` 打成独立分发包 |
| `android/` | Kotlin + Jetpack Compose Material 3 客户端，使用同一份插件 JSON |

## WebView2 宿主

- Wails v2 只有单窗口/单 WebView，因此阅读器、侧栏和小窗由 `internal/webhost` 自绘：
  - 阅读器/侧栏：Wails 主窗口的子 HWND，bounds 由 UI 以 CSS px 传入，按窗口 DPI 换算为物理像素。
  - 微软翻译窗口、ChatGPT 小窗：owner 顶层窗口，关闭时隐藏，可再次打开。
- 每个角色使用独立 WebView2 数据目录：`reader`、`chat`、`chat-popup`、`translator`、`translator-window`。
- 轻量存储模式把上述目录切换到 `%TEMP%\Lumen-<pid>`，退出时删除。
- 远程页面不加载 Wails runtime；只通过 `window.chrome.webview.postMessage` 发送 `selection` / `location` / `extract-result` / `eval-result`。
- 阅读视图注入的脚本只做划词监听、位置上报和按需 Readability 提取；提取结果由 `reader-extract` 返回。
- 页面 `DOMContentLoaded` / `load` 后注入启用的去广告插件脚本；规则按域名合并，只移除广告节点，MutationObserver 处理动态插入的广告。
- 打开文章后前端等待页面就绪，自动调用 `reader-extract` 进入双语精读；来源首页的目录入口不自动触发。

## 数据与安全

- 资料库、密钥、插件和网站资料都在 `%APPDATA%\Lumen`；日志只输出错误与初始化信息。
- `reader` 使用独立的持久 WebView2 配置目录；翻译与聊天各自隔离。
- 翻译密钥通过 DPAPI 加密；备份不含密钥。
- 打开文章后自动授权当前正文的本次翻译；仍会先匿名核对公开性并在界面标注结果。授权 token 绑定提供方、段落集合和邻段语境。
- 应用不内置插件；插件 ZIP 只接受根目录或一级目录内的 `plugin.json`，JS 只解析 `module.exports` / `export default` / `window.__LUMEN_PLUGIN__` 中的 JSON 对象，不执行脚本。ID 限制为安全字符，ZIP 解压总体积上限 32 MB（Android 20 MB），插件可移除。
- 网站权限与下载默认拒绝；新窗口按 http(s) 规则处理，外部协议交给系统。

## 构建安装包

```sh
go run ./cmd/package-plugins
wails build -platform windows/amd64 -nsis -installscope user -webview2 download
```

Wails 生成 `build/bin/Lumen-amd64-installer.exe`。发布时可重命名为 `Lumen-Setup-2.0.0.exe`。安装包未签名，不自动发布。

## 当前限制

- ChatGPT 需要网络可达并自行登录；网络不可达时侧栏显示连接失败提示。
- DeepSeek 默认内嵌官方网页（需要登录）；切到 API 问答时需要密钥，密钥只保存在本机 DPAPI 文件中，不进入备份。
- 自动精读依赖 Readability 能提取正文；提取失败时保留原站阅读视图并提示原因。
- 启动时清理 WebView2 的组件/GPU/HTTP 缓存并限制缓存上限，保留 Cookie 与登录态。旧 Electron 缓存需手动清理。
- 微软网页翻译（`microsoft-web`，双端默认）走 Edge 浏览器自带的免密钥接口 `edge.microsoft.com/translate/translatetext`，请求需带浏览器 UA 与 `Origin`/`Referer: microsoft.com`。该接口非公开，可能被限流或改版：桌面端失败时自动回退到隐藏 WebView2 抓取 Bing 翻译页，安卓端提示切换微软 API / DeepL / MyMemory。
- 去广告效果取决于插件规则与站点当前 DOM；规则只隐藏广告，不影响正文和登录态。
- 未安装爬虫插件时媒体目录与各站前三为空，界面会引导到插件中心；官方插件 ZIP 位于 `dist/plugins/`。
- 旧 Electron 版加密的 API 密钥不能迁移，需要重新录入。
- 轻量存储与网站数据清理的网站目录变更在重启后完成。
