# 第三方声明

Lumen 应用自有源码采用 [MIT](LICENSE)。下列第三方组件保留其原始许可证；发布或修改时应同步保留许可证与来源说明。

## 插件数据与去广告引擎

`plugins/lumen-crawler/`、`plugins/lumen-adblock/` 与 `plugins/runtime/ads-engine.js` 由 Lumen 项目维护，采用与应用一致的 MIT 许可证。插件只包含来源清单、首页提取规则和去广告选择器，不包含或执行第三方代码。应用不嵌入插件清单；官方插件通过 `dist/plugins/*.zip` 独立分发，桌面与 Android 共用同一份包。

## Mozilla Readability

`internal/extractor/readability.js` 来自 Mozilla Readability，版权归 Arc90 Inc 及贡献者所有，采用 Apache License 2.0。许可证全文保存在 [internal/extractor/LICENSE-Readability.md](internal/extractor/LICENSE-Readability.md)。该脚本在阅读视图的页面内执行，仅用于构造精读段落。

## legado-with-MD3（Android 界面组件）

`android/` 中的拖拽手势观察、可交互高光（AGSL 径向高光与按压形变）与玻璃配方参数改编自 **legado-with-MD3**（`https://github.com/HapeLee/legado-with-MD3`，GNU GPLv3，`gedoor/legado` 的 Material 3 / Compose 分支）。受影响的文件在头部标注了来源与改动范围；该模块整体以 GPLv3 发布，见 [android/NOTICE](android/NOTICE) 与 [android/LICENSE](android/LICENSE)。仓库其余部分仍为 MIT。

## adblock_auto（去广告规则）

`plugins/lumen-adblock` 的通用选择器子集与部分域名规则由 `cmd/import-adblock` 从 **adblock_auto**（`https://github.com/lingeringsound/adblock_auto`，GPL-3.0）提取，插件因此以 GPL-3.0 分发（见 [plugins/lumen-adblock/NOTICE](plugins/lumen-adblock/NOTICE)）。只导入外观过滤选择器，网络拦截与脚本注入规则不会被使用。

## 观读轻译 · 双语阅读（桌面译文显示与提问小窗）

桌面端的译文排版系统与阅读提问小窗移植自 **观读轻译 1.4.1**（`guandu-lite-extension`，作者提供的 Chrome/Edge 扩展）：

- `ui/guandu-appearance.js` ← 扩展 `shared/appearance.js`（字体族、配色表与译文套用逻辑逐行保留，仅去掉扩展与后台通信的最后一块，并把 `resolveTheme` 一并导出给设置预览使用）。
- `ui/guandu.css` ← 扩展 `content/page.css` 的译文节点规则与 `content/chat.js` 内联的小窗样式，逐条保留；只把 `.lite-translate-output[data-lite-translate-owned]` 改名为 `.gd-translation[data-guandu-owned]`，并新增一层把扩展的界面变量映射到 Lumen 主题变量的适配规则。设置编辑器样式来自扩展 `ui/lumen.css` 的 `.translation-display-editor`。
- `ui/guandu-chat-format.js` ← 扩展 `content/chat-format.js`（Markdown 解析 + emoji 分段 + 白名单 DOM 构建）逐行保留。
- `ui/guandu-chat.js` ← 扩展 `content/chat.js` 的界面结构与交互（悬浮球、停靠、用选文 / 附带本文、快捷问题、停止、新对话、复制回答、历史裁剪规则）；`chrome.runtime` 通信换成 Lumen 的 `GuanduAsk` / `GuanduCancel` 绑定，文章标题与网址改由当前阅读条目提供。
- 显示配置字段与取值、提问窗口配置与问答消息链分别对应扩展的 `core/display.mjs`、`core/chat-config.mjs`、`core/chat.mjs`，长度上限（4,000 / 18,000 / 12,000 / 48,000、四轮历史）保持一致；提示词原文照搬。
- `ui/vendor/markdown-it.min.js`（markdown-it 15.0.3，MIT，Copyright (c) 2014 Vitaly Puzrin, Alex Kocharin）与 `ui/vendor/markdown-it-emoji.min.js`（markdown-it-emoji 3.1.0，MIT，Copyright (c) 2014 Vitaly Puzrin）按扩展原样打包，许可证见 [ui/vendor/LICENSE-markdown-it.txt](ui/vendor/LICENSE-markdown-it.txt)、[ui/vendor/LICENSE-markdown-it-emoji.txt](ui/vendor/LICENSE-markdown-it-emoji.txt)，其传递依赖许可证保存在 [ui/vendor/licenses/](ui/vendor/licenses/)（argparse PSF-2.0、entities BSD-2-Clause、linkify-it、mdurl、punycode.js、uc.micro MIT）。

扩展自身的其余能力（术语库、专家、连续翻译、配置导入导出等）不在本次移植范围内；Lumen 的翻译请求仍由 Go 侧的翻译服务发出，不加载扩展脚本。

## Android 依赖

- Backdrop / Liquid Glass（`io.github.kyant0:backdrop`，含传递依赖 `io.github.kyant0:shapes`）：Apache License 2.0，用于 Android 端液态玻璃的背景采样、模糊与 AGSL 折射。版权归 Kyant0 及贡献者所有。

## Go 依赖

应用通过 Go modules 使用以下主要依赖，各自的许可证随模块分发：

- Wails v2（MIT）
- wailsapp/go-webview2（MIT）
- mmcdole/gofeed（MIT）
- PuerkitoBio/goquery（BSD-3-Clause）
- golang.org/x/sys、golang.org/x/net、golang.org/x/text（BSD-3-Clause）
- google/uuid（BSD-3-Clause）

## 内容与服务

媒体内容、商标、ChatGPT、DeepSeek、Microsoft Translator、DeepL、MyMemory 及词典服务的权利由各自所有者保留。应用不声称拥有这些内容或服务。DeepSeek 问答只在用户点击提问时调用其官方 API，密钥由用户自行提供。
