package feed

import (
	"testing"

	"lumen/internal/model"
)

const sampleRSS = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"><channel><title>Example</title><link>https://example.com</link>
<item><title>Test headline from feed</title><link>https://example.com/a</link><description>Feed description</description><pubDate>Wed, 23 Oct 2026 10:00:00 GMT</pubDate></item>
<item><title>Bad link</title><link>javascript:alert(1)</link></item>
</channel></rss>`

func TestParseFeed(t *testing.T) {
	source := model.Source{ID: "test", Name: "Test", Category: "新闻", Region: "全球", URL: "https://example.com", Feed: "https://example.com/rss", Reach: 4}
	items, err := Parse([]byte(sampleRSS), source, 1, 25)
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 1 {
		t.Fatalf("items = %d", len(items))
	}
	item := items[0]
	if item.Title != "Test headline from feed" || item.URL != "https://example.com/a" || item.SourceID != "test" {
		t.Fatalf("unexpected item: %+v", item)
	}
	if item.PublishedAt == nil {
		t.Fatal("published date missing")
	}
}
