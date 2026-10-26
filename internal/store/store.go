package store

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"lumen/internal/model"
)

type Repo struct {
	Dir  string
	Path string
}

func UserDataDir() string {
	return filepath.Join(os.Getenv("APPDATA"), "Lumen")
}

func LegacyLibraryPath() string {
	return filepath.Join(os.Getenv("APPDATA"), "lumen-reader", "library.json")
}

func New(dir string) *Repo {
	return &Repo{Dir: dir, Path: filepath.Join(dir, "library.json")}
}

func (r *Repo) Load() (model.Store, error) {
	store := model.DefaultStore()
	if err := os.MkdirAll(r.Dir, 0o755); err != nil {
		return store, err
	}

	path := r.Path
	if _, err := os.Stat(path); os.IsNotExist(err) {
		if legacy := LegacyLibraryPath(); fileExists(legacy) {
			path = legacy
		}
	}

	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return store, nil
		}
		return store, err
	}

	if err := json.Unmarshal(data, &store); err != nil {
		recovery := fmt.Sprintf("%s.recovery-%d", r.Path, time.Now().UnixMilli())
		_ = os.WriteFile(recovery, data, 0o644)
		return model.DefaultStore(), fmt.Errorf("读取资料库失败，已保留恢复副本: %w", err)
	}
	if path != r.Path {
		backup := fmt.Sprintf("%s.lumen-migration-%d", path, time.Now().UnixMilli())
		_ = os.WriteFile(backup, data, 0o644)
	}
	normalize(&store)
	if store.Settings.LowStorage {
		store.Articles = []model.Article{}
		store.Translations = map[string]model.Translation{}
		store.Statuses = map[string]model.Status{}
		store.Newsroom = nil
		store.LastRefresh = nil
	}
	return store, nil
}

func (r *Repo) Save(store model.Store) error {
	disk := diskState(store)
	data, err := json.Marshal(disk)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(r.Dir, 0o755); err != nil {
		return err
	}
	tmp := r.Path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, r.Path)
}

func diskState(store model.Store) model.Store {
	if !store.Settings.LowStorage {
		return store
	}
	store.Articles = []model.Article{}
	store.Translations = map[string]model.Translation{}
	store.Statuses = map[string]model.Status{}
	store.Newsroom = nil
	store.NewsroomCleared = true
	store.ArticlesCleared = true
	return store
}

func normalize(store *model.Store) {
	if store.Version == 0 {
		store.Version = 1
	}
	if store.Articles == nil {
		store.Articles = []model.Article{}
	}
	if store.Vocabulary == nil {
		store.Vocabulary = []model.Vocab{}
	}
	if store.Saved == nil {
		store.Saved = []model.SavedArticle{}
	}
	if store.History == nil {
		store.History = []model.HistoryEntry{}
	}
	if store.Statuses == nil {
		store.Statuses = map[string]model.Status{}
	}
	if store.Translations == nil {
		store.Translations = map[string]model.Translation{}
	}
	if store.Settings.Interests == nil {
		store.Settings.Interests = []string{}
	}
	if store.Settings.DisabledSources == nil {
		store.Settings.DisabledSources = []string{}
	}
	if store.Settings.DisabledPlugins == nil {
		store.Settings.DisabledPlugins = []string{}
	}
	if store.Settings.Weights == (model.Weights{}) {
		store.Settings.Weights = model.DefaultWeights
	}
	if store.Settings.Theme == "" {
		store.Settings.Theme = "sage"
	}
	if store.Settings.FontSize == 0 {
		store.Settings.FontSize = 18
	}
	if store.Settings.Translation.Provider == "" {
		store.Settings.Translation.Provider = "microsoft-web"
	}
	if store.Settings.Translation.DeeplPlan == "" {
		store.Settings.Translation.DeeplPlan = "free"
	}
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}
