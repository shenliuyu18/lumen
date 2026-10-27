package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"lumen/internal/model"
	"lumen/internal/netclient"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"
)

func main() {
	data, err := os.ReadFile(os.Args[1])
	if err != nil {
		panic(err)
	}
	var input struct {
		Newsroom model.Newsroom `json:"newsroom"`
	}
	if err = json.Unmarshal(data, &input); err != nil {
		panic(err)
	}
	client := netclient.New(10 * time.Second)
	queue := make(chan model.Article)
	var wg sync.WaitGroup
	var mu sync.Mutex
	results := []map[string]any{}
	start := time.Now()
	for n := 0; n < 12; n++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for article := range queue {
				ctx, cancel := context.WithTimeout(context.Background(), 9*time.Second)
				request, err := http.NewRequestWithContext(ctx, "GET", article.URL, nil)
				code := 0
				finalURL := ""
				message := ""
				if err == nil {
					request.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/134.0.0.0 Safari/537.36")
					var response *http.Response
					response, err = client.Do(request)
					if err == nil {
						code = response.StatusCode
						finalURL = response.Request.URL.String()
						io.CopyN(io.Discard, response.Body, 32768)
						response.Body.Close()
					}
				}
				if err != nil {
					message = err.Error()
				}
				cancel()
				mu.Lock()
				results = append(results, map[string]any{"source": article.SourceID, "url": article.URL, "status": code, "finalURL": finalURL, "error": message})
				mu.Unlock()
			}
		}()
	}
	for _, g := range input.Newsroom.Groups {
		for _, a := range g.Items {
			queue <- a
		}
	}
	close(queue)
	wg.Wait()
	ok := 0
	for _, r := range results {
		if code := r["status"].(int); code >= 200 && code < 300 {
			ok++
		}
	}
	out, _ := json.MarshalIndent(map[string]any{"checkedAt": time.Now().Format(time.RFC3339), "seconds": time.Since(start).Seconds(), "total": len(results), "httpOK": ok, "results": results}, "", "  ")
	os.MkdirAll(filepath.Dir(os.Args[2]), 0755)
	os.WriteFile(os.Args[2], out, 0644)
	fmt.Printf("%d/%d article URLs returned HTTP 2xx, %.1fs\n", ok, len(results), time.Since(start).Seconds())
}
