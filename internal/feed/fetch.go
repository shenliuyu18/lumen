package feed

import (
	"context"
	"errors"
	"fmt"
	"io"
	"lumen/internal/netclient"
	"net/http"
	"sync"
	"time"
)

type Fetcher struct{ Client *http.Client }

func NewFetcher() *Fetcher { return &Fetcher{Client: netclient.New(0)} }

type fetchResult struct {
	done chan struct{}
	data []byte
	err  error
}

// Round shares both in-flight and finished requests only within one refresh.
// Its memory is released after refresh; future refreshes always revalidate the network.
func (f *Fetcher) Round() func(context.Context, string, time.Duration) ([]byte, error) {
	var mu sync.Mutex
	results := map[string]*fetchResult{}
	return func(ctx context.Context, url string, timeout time.Duration) ([]byte, error) {
		mu.Lock()
		entry, ok := results[url]
		if !ok {
			entry = &fetchResult{done: make(chan struct{})}
			results[url] = entry
		}
		mu.Unlock()
		if ok {
			select {
			case <-entry.done:
				return entry.data, entry.err
			case <-ctx.Done():
				return nil, ctx.Err()
			}
		}
		entry.data, entry.err = f.Fetch(ctx, url, timeout)
		close(entry.done)
		return entry.data, entry.err
	}
}

func (f *Fetcher) Fetch(ctx context.Context, url string, timeout time.Duration) ([]byte, error) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	var last error
	for attempt := 0; attempt < 2; attempt++ {
		data, retry, err := f.request(ctx, url)
		if err == nil {
			return data, nil
		}
		last = err
		if !retry || ctx.Err() != nil {
			break
		}
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-time.After(180 * time.Millisecond):
		}
	}
	return nil, last
}

func (f *Fetcher) request(ctx context.Context, url string) ([]byte, bool, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, false, err
	}
	request.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/134.0.0.0 Safari/537.36 Lumen/1.0.1")
	request.Header.Set("Accept", "application/rss+xml, application/atom+xml, application/xml, text/html;q=0.9, */*;q=0.8")
	request.Header.Set("Accept-Language", "en-US,en;q=0.9")
	response, err := f.Client.Do(request)
	if err != nil {
		return nil, !errors.Is(err, context.Canceled), err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, response.StatusCode == 502 || response.StatusCode == 503 || response.StatusCode == 504, fmt.Errorf("HTTP %d", response.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, 6_000_001))
	if len(data) > 6_000_000 {
		return nil, false, fmt.Errorf("内容过大")
	}
	return data, err != nil, err
}

func ErrorMessage(err error) string {
	if err == nil {
		return ""
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return "连接超时，请检查系统代理或网络；保留上次内容"
	}
	if errors.Is(err, context.Canceled) {
		return "更新已取消；保留上次内容"
	}
	return err.Error()
}
