//go:build windows

package netclient

import (
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
