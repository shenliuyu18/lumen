package service

import (
	"os"
	"path/filepath"
)

var webViewCachePaths = []string{
	"EBWebView/component_crx_cache",
	"EBWebView/WidevineCdm",
	"EBWebView/Subresource Filter",
	"EBWebView/BrowserMetrics",
	"EBWebView/GrShaderCache",
	"EBWebView/ShaderCache",
	"EBWebView/Speech Recognition",
	"EBWebView/Default/Cache",
	"EBWebView/Default/Code Cache",
	"EBWebView/Default/GPUCache",
	"EBWebView/Default/DawnGraphiteCache",
	"EBWebView/Default/DawnWebGPUCache",
}

// uiProfile 是应用界面自己的档案；其余目录都是打开过的网站留下的。
const uiProfile = "ui"

// webProfiles 直接枚举磁盘：新增或废弃的网站档案都不需要再维护一份名字清单。
func webProfiles(root string, includeUI bool) []string {
	entries, err := os.ReadDir(root)
	if err != nil {
		return nil
	}
	profiles := make([]string, 0, len(entries))
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		if entry.Name() == uiProfile && !includeUI {
			continue
		}
		profiles = append(profiles, entry.Name())
	}
	return profiles
}

func trimWebViewCaches(dataDir string) {
	root := filepath.Join(dataDir, "webview2")
	for _, role := range webProfiles(root, true) {
		for _, relative := range webViewCachePaths {
			_ = os.RemoveAll(filepath.Join(root, role, relative))
		}
	}
}
