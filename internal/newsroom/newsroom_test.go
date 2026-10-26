package newsroom

import (
	"context"
	"errors"
	"testing"
	"time"

	"lumen/internal/model"
)

func source() model.Source {
	return model.Source{ID: "test", Name: "Test", Category: "新闻", Region: "全球", URL: "https://example.com", Reach: 4}
}

func TestHomepageHeadlinesFiltersNavigationAndURLs(t *testing.T) {
	html := `<a href="/aboutthefed.htm"><h2>Board of Governors of the Federal Reserve System</h2></a><a href="/news/original-story" aria-label="https://example.com/news/original-story">Actual headline with meaningful public interest</a><a href="/news/url-only" aria-label="https://example.com/news/url-only"></a>`
	items := HomepageHeadlines(html, source(), time.Now().UnixMilli())
	if len(items) != 1 || items[0].Title != "Actual headline with meaningful public interest" {
		t.Fatalf("unexpected items: %+v", items)
	}
}

func TestHomepageHeadlinesPreservesOrderAndCaps(t *testing.T) {
	html := `<nav><a href="/news/nav"><h2>Navigation item that is not a story</h2></a></nav>`
	for i := 1; i <= 4; i++ {
		html += `<article><a href="/news/story-` + string(rune('0'+i)) + `"><h2>Original headline number ` + string(rune('0'+i)) + ` with evidence</h2></a></article>`
	}
	html += `<a href="/news/story-1"><h3>Duplicate heading should not be repeated</h3></a>`
	items := HomepageHeadlines(html, source(), time.Now().UnixMilli())
	if len(items) != 3 {
		t.Fatalf("items = %d", len(items))
	}
	if items[0].Title != "Original headline number 1 with evidence" || items[2].Title != "Original headline number 3 with evidence" {
		t.Fatalf("unexpected titles: %+v", items)
	}
}

func TestFailedSourceKeepsStaleGroup(t *testing.T) {
	prior := model.NewsroomGroup{
		SourceID:  "test",
		Items:     []model.Article{{ID: "old", Title: "Older headline", URL: "https://example.com/a"}},
		FetchedAt: func() *int64 { value := int64(1); return &value }(),
		Basis:     func() *string { value := "homepage"; return &value }(),
	}
	result := Update(context.Background(), Options{
		Sources:    []model.Source{source()},
		Fetch:      func(context.Context, string, time.Duration) ([]byte, error) { return nil, errors.New("offline") },
		Previous:   map[string]model.NewsroomGroup{"test": prior},
		OnProgress: func(int, int, string) {},
	})
	if len(result.Groups) != 1 || !result.Groups[0].Stale || len(result.Groups[0].Items) != 1 || result.Groups[0].Error == nil || *result.Groups[0].Error != "offline" {
		t.Fatalf("unexpected stale group: %+v", result.Groups)
	}
}
