// Read-only live diagnostics; all output is written to a specified report directory.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"lumen/internal/feed"
	"lumen/internal/model"
	"lumen/internal/newsroom"
	"os"
	"path/filepath"
	"sync"
	"time"
)

func main() {
	manifest := flag.String("plugin", "plugins/lumen-crawler/plugin.json", "source plugin")
	output := flag.String("out", "test-results/network.json", "diagnostic report")
	flag.Parse()
	data, err := os.ReadFile(*manifest)
	if err != nil {
		panic(err)
	}
	var plugin struct {
		Sources []model.Source      `json:"sources"`
		Spec    *model.NewsroomSpec `json:"newsroom"`
	}
	if err = json.Unmarshal(data, &plugin); err != nil {
		panic(err)
	}
	start := time.Now()
	ctx, cancel := context.WithTimeout(context.Background(), 110*time.Second)
	defer cancel()
	fetcher := feed.NewFetcher()
	shared := fetcher.Round()
	var mu sync.Mutex
	requests := 0
	failures := map[string]string{}
	fetch := func(ctx context.Context, url string, t time.Duration) ([]byte, error) {
		data, err := shared(ctx, url, t)
		mu.Lock()
		requests++
		if err != nil {
			failures[url] = feed.ErrorMessage(err)
		}
		mu.Unlock()
		return data, err
	}
	result := newsroom.Update(ctx, newsroom.Options{Sources: plugin.Sources, Spec: plugin.Spec, Fetch: fetch, Concurrency: 12})
	report := map[string]any{"at": time.Now().Format(time.RFC3339), "seconds": time.Since(start).Seconds(), "requests": requests, "failures": failures, "newsroom": result}
	// Probe primary subscription endpoints independently of HTML parsing.
	feeds := map[string]any{}
	jobs := make(chan model.Source)
	var wg sync.WaitGroup
	for n := 0; n < 12; n++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for source := range jobs {
				if source.Feed == "" {
					continue
				}
				data, err := shared(ctx, source.Feed, 9*time.Second)
				count := 0
				if err == nil {
					items, parseErr := feed.Parse(data, source, time.Now().UnixMilli(), 25)
					err = parseErr
					count = len(items)
				}
				mu.Lock()
				feeds[source.ID] = map[string]any{"count": count, "error": feed.ErrorMessage(err)}
				mu.Unlock()
			}
		}()
	}
	for _, s := range plugin.Sources {
		jobs <- s
	}
	close(jobs)
	wg.Wait()
	report["feeds"] = feeds
	os.MkdirAll(filepath.Dir(*output), 0755)
	encoded, _ := json.MarshalIndent(report, "", "  ")
	os.WriteFile(*output, encoded, 0644)
	fmt.Printf("%d/%d sources, %d stories, %.1f seconds\n", result.CoveredCount, result.SourceCount, result.StoryCount, time.Since(start).Seconds())
}
