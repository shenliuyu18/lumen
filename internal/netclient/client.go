// Package netclient keeps background requests on the same network path as WebView2.
package netclient

import (
	"errors"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync/atomic"
	"time"
)

var configured atomic.Pointer[proxyConfig]

type proxyConfig struct {
	direct bool
	url    *url.URL
}

func New(timeout time.Duration) *http.Client {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.Proxy = Proxy
	transport.MaxIdleConns = 64
	transport.MaxIdleConnsPerHost = 4
	transport.MaxConnsPerHost = 6
	transport.ResponseHeaderTimeout = 12 * time.Second
	transport.TLSHandshakeTimeout = 8 * time.Second
	return &http.Client{Transport: transport, Timeout: timeout}
}

// Configure overrides proxy discovery for the process. Modes: "" or "system"
// follows Windows, "direct" never proxies, "manual" uses raw (host:port or URL).
func Configure(mode, raw string) error {
	switch strings.TrimSpace(mode) {
	case "", "system":
		configured.Store(nil)
		return nil
	case "direct":
		configured.Store(&proxyConfig{direct: true})
		return nil
	case "manual":
		spec := strings.TrimSpace(raw)
		if spec == "" {
			// 手动模式还没填地址时退回系统代理，等用户填好再切换。
			configured.Store(nil)
			return nil
		}
		if !strings.Contains(spec, "://") {
			spec = "http://" + spec
		}
		parsed, err := url.Parse(spec)
		if err != nil || parsed.Host == "" {
			return errors.New("代理地址无效")
		}
		configured.Store(&proxyConfig{url: parsed})
		return nil
	default:
		return errors.New("未知的代理模式")
	}
}

func Proxy(req *http.Request) (*url.URL, error) {
	if cfg := configured.Load(); cfg != nil {
		if cfg.direct {
			return nil, nil
		}
		return cfg.url, nil
	}
	// Explicit environment configuration takes precedence; retain NO_PROXY semantics.
	for _, key := range []string{"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"} {
		if os.Getenv(key) != "" {
			return http.ProxyFromEnvironment(req)
		}
	}
	return systemProxy(req)
}

func parseProxy(spec, scheme string) (*url.URL, error) {
	value := ""
	for _, part := range strings.Split(spec, ";") {
		pair := strings.SplitN(strings.TrimSpace(part), "=", 2)
		if len(pair) == 1 && value == "" {
			value = pair[0]
		}
		if len(pair) == 2 && pair[0] == scheme {
			value = pair[1]
			break
		}
	}
	if value == "" {
		return nil, nil
	}
	if !strings.Contains(value, "://") {
		value = "http://" + value
	}
	return url.Parse(value)
}
