# 付费墙清理 · BPC 精简

规则取自 **bypass-paywalls-chrome-clean**（MIT，magnolia1234），只保留其中可以声明式表达的部分：

- 付费墙/订阅脚本域名（tinypass、piano、poool、zephr、sophi、pelcro 等）
- 站内常见付费墙遮挡层选择器（`[class*="paywall"]`、`.tp-modal`、`.regwall` 等）

Lumen 会把这些规则合并进现有的去广告引擎：拦截对应脚本请求，并在原站与阅读视图里移除遮挡层。

**不包含**：按站点执行脚本、修改 UA、从 archive.today/Google 缓存抓取全文、绕过登录或服务端截断。原文由站点服务端截断的媒体，本插件无法解锁；它只处理前端遮挡。

限制与来源见仓库根目录 `NOTICE`；BPC 许可证为 MIT。
