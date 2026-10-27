// Package netclient keeps background requests on the same network path as WebView2.
package netclient

import (
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

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

func Proxy(req *http.Request) (*url.URL, error) {
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
