package catalog

import (
	"embed"
	"encoding/json"
	"fmt"

	"lumen/internal/model"
)

//go:embed snapshot.json newsroom-snapshot.json
var files embed.FS

type Seed struct {
	FetchedAt int64                   `json:"fetchedAt"`
	Articles  []model.Article         `json:"articles"`
	Statuses  map[string]model.Status `json:"statuses"`
}

func Snapshot() (Seed, error) {
	data, err := files.ReadFile("snapshot.json")
	if err != nil {
		return Seed{}, err
	}
	var seed Seed
	if err := json.Unmarshal(data, &seed); err != nil {
		return Seed{}, fmt.Errorf("decode snapshot: %w", err)
	}
	return seed, nil
}

func NewsroomSnapshot() (*model.Newsroom, error) {
	data, err := files.ReadFile("newsroom-snapshot.json")
	if err != nil {
		return nil, err
	}
	var newsroom model.Newsroom
	if err := json.Unmarshal(data, &newsroom); err != nil {
		return nil, fmt.Errorf("decode newsroom snapshot: %w", err)
	}
	return &newsroom, nil
}
