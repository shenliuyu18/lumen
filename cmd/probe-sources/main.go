package main

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/PuerkitoBio/goquery"
	"lumen/internal/feed"
	"lumen/internal/model"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

func main() {
	input, err := os.ReadFile(os.Args[1])
	if err != nil {
		panic(err)
	}
	var urls []string
	if err = json.Unmarshal(input, &urls); err != nil {
		panic(err)
	}
	fetcher := feed.NewFetcher()
	var wg sync.WaitGroup
	var mu sync.Mutex
	result := map[string]any{}
	queue := make(chan string)
	for n := 0; n < 8; n++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for target := range queue {
				data, err := fetcher.Fetch(context.Background(), target, 9*time.Second)
				r := map[string]any{"error": feed.ErrorMessage(err), "bytes": len(data)}
				if err == nil {
					items, parseErr := feed.Parse(data, model.Source{ID: "probe", Name: "Probe", URL: target}, time.Now().UnixMilli(), 3)
					r["feedItems"] = len(items)
					if parseErr == nil && len(items) > 0 {
						r["firstTitle"] = items[0].Title
					}
					doc, _ := goquery.NewDocumentFromReader(strings.NewReader(string(data)))
					links := []string{}
					if doc != nil {
						doc.Find("a[href],link[href]").Each(func(_ int, s *goquery.Selection) {
							href, _ := s.Attr("href")
							if strings.Contains(href, "rss") || strings.Contains(href, "feed") {
								links = append(links, href)
							}
						})
					}
					r["feedLinks"] = links
					candidates := []map[string]string{}
					if doc != nil {
						doc.Find("a[href]").Each(func(_ int, a *goquery.Selection) {
							text := strings.Join(strings.Fields(a.Text()), " ")
							href, _ := a.Attr("href")
							if len(text) > 15 && len(text) < 250 && len(candidates) < 70 {
								candidates = append(candidates, map[string]string{"href": href, "text": text})
							}
						})
					}
					r["candidates"] = candidates
				}
				mu.Lock()
				result[target] = r
				mu.Unlock()
			}
		}()
	}
	for _, u := range urls {
		queue <- u
	}
	close(queue)
	wg.Wait()
	out, _ := json.MarshalIndent(result, "", "  ")
	os.MkdirAll(filepath.Dir(os.Args[2]), 0755)
	os.WriteFile(os.Args[2], out, 0644)
	fmt.Println(string(out))
}
