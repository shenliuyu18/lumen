package main

import (
	"context"
	"encoding/json"
	"fmt"
	"lumen/internal/service"
	"os"
	"path/filepath"
	"sync"
	"time"
)

func main() {
	output, err := filepath.Abs(os.Args[1])
	if err != nil {
		panic(err)
	}
	os.Setenv("APPDATA", filepath.Join(output, "profile-network"))
	os.MkdirAll(output, 0755)
	svc, err := service.New()
	if err != nil {
		panic(err)
	}
	if _, _, err = svc.InstallPlugin("dist/plugins/lumen-crawler-1.0.1.zip"); err != nil {
		panic(err)
	}
	started := time.Now()
	firstUpdate := float64(0)
	var mu sync.Mutex
	svc.SetEmitter(func(event string, data any) {
		if event == "library-updated" {
			mu.Lock()
			if firstUpdate == 0 {
				firstUpdate = time.Since(started).Seconds()
			}
			mu.Unlock()
		}
	})
	state := svc.Refresh(context.Background())
	fresh := 0
	for _, g := range state.Newsroom.Groups {
		if len(g.Items) > 0 && !g.Stale {
			fresh++
		}
	}
	feeds := 0
	for _, status := range state.Statuses {
		if status.OK {
			feeds++
		}
	}
	summary := map[string]any{"seconds": time.Since(started).Seconds(), "firstProgressUpdateSeconds": firstUpdate, "sources": len(state.Sources), "freshSources": fresh, "coveredIncludingOldSnapshot": state.Newsroom.CoveredCount, "storiesIncludingOldSnapshot": state.Newsroom.StoryCount, "successfulFeeds": feeds, "articles": len(state.Articles)}
	data, _ := json.MarshalIndent(state, "", "  ")
	os.WriteFile(filepath.Join(output, "service-state.json"), data, 0644)
	data, _ = json.MarshalIndent(summary, "", "  ")
	os.WriteFile(filepath.Join(output, "summary.json"), data, 0644)
	fmt.Println(string(data))
}
