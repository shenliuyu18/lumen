//go:build windows

package netclient

import (
	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
	"net/http"
	"net/url"
	"path"
	"strings"
)

func systemProxy(req *http.Request) (*url.URL, error) {
	key, err := registry.OpenKey(registry.CURRENT_USER, `Software\Microsoft\Windows\CurrentVersion\Internet Settings`, registry.QUERY_VALUE)
	if err != nil {
		return nil, nil
	}
	defer key.Close()
	enabled, _, _ := key.GetIntegerValue("ProxyEnable")
	if enabled == 0 {
		return nil, nil
	}
	bypass, _, _ := key.GetStringValue("ProxyOverride")
	host := strings.ToLower(req.URL.Hostname())
	for _, pattern := range strings.Split(bypass, ";") {
		pattern = strings.ToLower(strings.TrimSpace(pattern))
		if pattern == "<local>" && !strings.Contains(host, ".") {
			return nil, nil
		}
		if match, _ := path.Match(pattern, host); pattern != "" && match {
			return nil, nil
		}
	}
	spec, _, _ := key.GetStringValue("ProxyServer")
	return parseProxy(spec, req.URL.Scheme)
}


var (
	wininetDLL            = windows.NewLazySystemDLL("wininet.dll")
	procInternetSetOption = wininetDLL.NewProc("InternetSetOptionW")
)

func notifyProxyChange() {
	// 39 = INTERNET_OPTION_SETTINGS_CHANGED, 37 = INTERNET_OPTION_REFRESH
	_, _, _ = procInternetSetOption.Call(0, 39, 0, 0)
	_, _, _ = procInternetSetOption.Call(0, 37, 0, 0)
}

// EnsureLocalBypass keeps wails.localhost out of the system proxy. The UI is
// served from that host, so a proxy would turn the window blank; proxy clients
// rewrite ProxyOverride regularly, therefore this runs on every launch.
func EnsureLocalBypass() {
	key, err := registry.OpenKey(registry.CURRENT_USER, `Software\Microsoft\Windows\CurrentVersion\Internet Settings`, registry.QUERY_VALUE|registry.SET_VALUE)
	if err != nil {
		return
	}
	defer key.Close()
	enabled, _, _ := key.GetIntegerValue("ProxyEnable")
	if enabled == 0 {
		return
	}
	bypass, _, _ := key.GetStringValue("ProxyOverride")
	for _, pattern := range strings.Split(bypass, ";") {
		if strings.EqualFold(strings.TrimSpace(pattern), "wails.localhost") {
			return
		}
	}
	updated := bypass
	if updated != "" && !strings.HasSuffix(updated, ";") {
		updated += ";"
	}
	updated += "wails.localhost"
	if err := key.SetStringValue("ProxyOverride", updated); err != nil {
		return
	}
	notifyProxyChange()
}
