package feed

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/mmcdole/gofeed"

	"lumen/internal/model"
	"lumen/internal/rank"
)

// 多数站点会拒绝非浏览器 UA，用桌面 Edge 的标识抓 RSS 才不会被 403。
const userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"

type Fetcher struct {
	Client *http.Client
}

func NewFetcher() *Fetcher {
	transport := &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: (&net.Dialer{
			Timeout:   8 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          96,
		MaxIdleConnsPerHost:   8,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ResponseHeaderTimeout: 16 * time.Second,
	}
	return &Fetcher{Client: &http.Client{Transport: transport}}
}

// Fetch 抓取一次失败时，只在超时/连接被重置这类瞬时错误上重试一次。
func (f *Fetcher) Fetch(ctx context.Context, url string, timeout time.Duration) ([]byte, error) {
	data, err := f.get(ctx, url, timeout)
	if err == nil || !retryable(err) {
		return data, err
	}
	select {
	case <-ctx.Done():
		return nil, FriendlyError(ctx.Err())
	case <-time.After(400 * time.Millisecond):
	}
	return f.get(ctx, url, timeout)
}

func (f *Fetcher) get(ctx context.Context, url string, timeout time.Duration) ([]byte, error) {
	requestCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	request, err := http.NewRequestWithContext(requestCtx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("User-Agent", userAgent)
	request.Header.Set("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, text/html;q=0.7, */*;q=0.5")
	request.Header.Set("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")
	request.Header.Set("Cache-Control", "no-cache")
	response, err := f.Client.Do(request)
	if err != nil {
		return nil, FriendlyError(err)
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, FriendlyError(fmt.Errorf("HTTP %d", response.StatusCode))
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, 6_000_001))
	if err != nil {
		return nil, FriendlyError(err)
	}
	if len(data) > 6_000_000 {
		return nil, errors.New("内容过大")
	}
	return data, nil
}

func retryable(err error) bool {
	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return true
	}
	text := err.Error()
	return strings.Contains(text, "connection reset") ||
		strings.Contains(text, "EOF") ||
		strings.Contains(text, "context deadline exceeded")
}

// FriendlyError 把抓取错误翻成能指导用户的中文说明。
func FriendlyError(err error) error {
	if err == nil {
		return nil
	}
	var netErr net.Error
	switch {
	case errors.As(err, &netErr) && netErr.Timeout(),
		errors.Is(err, context.DeadlineExceeded),
		strings.Contains(err.Error(), "context deadline exceeded"):
		return errors.New("网络超时：该站点当前不可达，稍后会自动重试")
	case strings.Contains(err.Error(), "HTTP 403"):
		return errors.New("HTTP 403：站点拒绝抓取，可能需要更换网络或代理")
	case strings.Contains(err.Error(), "HTTP 429"):
		return errors.New("HTTP 429：请求过于频繁，请稍后再试")
	case strings.Contains(err.Error(), "HTTP 5"):
		return fmt.Errorf("%s：站点暂时故障", err.Error())
	case strings.Contains(err.Error(), "no such host"):
		return errors.New("域名解析失败：检查网络或 DNS")
	}
	return err
}

func Parse(data []byte, source model.Source, fetchedAt int64, limit int) ([]model.Article, error) {
	parser := gofeed.NewParser()
	parsed, err := parser.ParseString(string(data))
	if err != nil {
		return nil, err
	}
	items := parsed.Items
	if limit > 0 && len(items) > limit {
		items = items[:limit]
	}
	articles := make([]model.Article, 0, len(items))
	for _, item := range items {
		description := item.Description
		if description == "" {
			description = item.Content
		}
		var published *int64
		if item.PublishedParsed != nil {
			value := item.PublishedParsed.UnixMilli()
			published = &value
		} else if item.UpdatedParsed != nil {
			value := item.UpdatedParsed.UnixMilli()
			published = &value
		}
		article := rank.Normalize(rank.NormalizeInput{
			Title:       item.Title,
			Link:        item.Link,
			Description: description,
			PublishedAt: published,
		}, source, fetchedAt)
		if article != nil {
			articles = append(articles, *article)
		}
	}
	return articles, nil
}
