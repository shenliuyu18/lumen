//go:build !windows

package netclient

import (
	"net/http"
	"net/url"
)

func systemProxy(*http.Request) (*url.URL, error) { return nil, nil }

func EnsureLocalBypass() {}
