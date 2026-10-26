# 第三方声明

Lumen 应用自有源码采用 [MIT](LICENSE)。下列第三方组件保留其原始许可证；发布或修改时应同步保留许可证与来源说明。

## 插件数据与去广告引擎

`plugins/lumen-crawler/`、`plugins/lumen-adblock/` 与 `plugins/runtime/ads-engine.js` 由 Lumen 项目维护，采用与应用一致的 MIT 许可证。插件只包含来源清单、首页提取规则和去广告选择器，不包含或执行第三方代码。应用不嵌入插件清单；官方插件通过 `dist/plugins/*.zip` 独立分发，桌面与 Android 共用同一份包。

## Mozilla Readability

`internal/extractor/readability.js` 来自 Mozilla Readability，版权归 Arc90 Inc 及贡献者所有，采用 Apache License 2.0。许可证全文保存在 [internal/extractor/LICENSE-Readability.md](internal/extractor/LICENSE-Readability.md)。该脚本在阅读视图的页面内执行，仅用于构造精读段落。

## legado-with-MD3（Android 界面组件）

`android/` 中的拖拽手势观察、可交互高光（AGSL 径向高光与按压形变）与玻璃配方参数改编自 **legado-with-MD3**（`https://github.com/HapeLee/legado-with-MD3`，GNU GPLv3，`gedoor/legado` 的 Material 3 / Compose 分支）。受影响的文件在头部标注了来源与改动范围；该模块整体以 GPLv3 发布，见 [android/NOTICE](android/NOTICE) 与 [android/LICENSE](android/LICENSE)。仓库其余部分仍为 MIT。

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
