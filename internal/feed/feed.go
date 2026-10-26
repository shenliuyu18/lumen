package feed

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/mmcdole/gofeed"

	"lumen/internal/model"
	"lumen/internal/rank"
)

type Fetcher struct {
	Client *http.Client
}

func NewFetcher() *Fetcher {
	return &Fetcher{Client: &http.Client{
		Transport: &http.Transport{Proxy: http.ProxyFromEnvironment},
	}}
}

func (f *Fetcher) Fetch(ctx context.Context, url string, timeout time.Duration) ([]byte, error) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("User-Agent", "LumenReader/1.0 (personal RSS reader)")
	response, err := f.Client.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, fmt.Errorf("HTTP %d", response.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, 6_000_001))
	if err != nil {
		return nil, err
	}
	if len(data) > 6_000_000 {
		return nil, fmt.Errorf("内容过大")
	}
	return data, nil
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
