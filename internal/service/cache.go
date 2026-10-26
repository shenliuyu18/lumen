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

var webViewRoles = []string{"ui", "reader", "chat", "chat-popup", "deepseek", "deepseek-popup", "translator", "translator-window"}

func trimWebViewCaches(dataDir string) {
	root := filepath.Join(dataDir, "webview2")
	for _, role := range webViewRoles {
		for _, relative := range webViewCachePaths {
			_ = os.RemoveAll(filepath.Join(root, role, relative))
		}
	}
}
