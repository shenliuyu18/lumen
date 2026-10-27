package netclient

import "testing"

func TestWindowsProxyFormats(t *testing.T) {
	for _, c := range []struct{ spec, scheme, want string }{{"127.0.0.1:10808", "https", "http://127.0.0.1:10808"}, {"http=a:80;https=b:8080", "https", "http://b:8080"}, {"http=a:80;https=b:8080", "http", "http://a:80"}, {"http=a:80", "https", ""}, {"socks5://localhost:1080", "https", "socks5://localhost:1080"}} {
		result, err := parseProxy(c.spec, c.scheme)
		got := ""
		if result != nil {
			got = result.String()
		}
		if err != nil || got != c.want {
			t.Fatalf("%s %s: %q %v", c.spec, c.scheme, got, err)
		}
	}
}
