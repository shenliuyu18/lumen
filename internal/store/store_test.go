package store

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"lumen/internal/model"
)

func TestSaveLoadRoundTrip(t *testing.T) {
	repo := New(t.TempDir())
	value := model.DefaultStore()
	value.Vocabulary = append(value.Vocabulary, model.Vocab{ID: "1", Text: "word", DueAt: 1})
	if err := repo.Save(value); err != nil {
		t.Fatal(err)
	}
	loaded, err := repo.Load()
	if err != nil {
		t.Fatal(err)
	}
	if len(loaded.Vocabulary) != 1 || loaded.Vocabulary[0].Text != "word" {
		t.Fatalf("unexpected load: %+v", loaded.Vocabulary)
	}
	if loaded.Settings.Theme != "sage" {
		t.Fatalf("defaults lost: %+v", loaded.Settings)
	}
}

func TestLowStorageDropsCaches(t *testing.T) {
	repo := New(t.TempDir())
	value := model.DefaultStore()
	value.Articles = []model.Article{{ID: "a", Title: "Article"}}
	value.Translations["k"] = model.Translation{Text: "译"}
	value.Statuses["s"] = model.Status{OK: true}
	value.Settings.LowStorage = true
	if err := repo.Save(value); err != nil {
		t.Fatal(err)
	}
	loaded, err := repo.Load()
	if err != nil {
		t.Fatal(err)
	}
	if len(loaded.Articles) != 0 || len(loaded.Translations) != 0 || len(loaded.Statuses) != 0 {
		t.Fatalf("low storage kept caches: %+v", loaded)
	}
}

func TestCorruptLibraryKeepsRecoveryCopy(t *testing.T) {
	dir := t.TempDir()
	repo := New(dir)
	if err := os.WriteFile(repo.Path, []byte("{not json"), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := repo.Load(); err == nil {
		t.Fatal("expected corruption error")
	}
	matches, _ := filepath.Glob(repo.Path + ".recovery-*")
	if len(matches) != 1 {
		t.Fatalf("recovery copies = %d", len(matches))
	}
}

func TestLegacyLibraryMigration(t *testing.T) {
	appData := t.TempDir()
	t.Setenv("APPDATA", appData)
	legacyDir := filepath.Join(appData, "lumen-reader")
	if err := os.MkdirAll(legacyDir, 0o755); err != nil {
		t.Fatal(err)
	}
	legacy := model.DefaultStore()
	legacy.Vocabulary = append(legacy.Vocabulary, model.Vocab{ID: "v1", Text: "legacy"})
	data, _ := json.Marshal(legacy)
	legacyPath := filepath.Join(legacyDir, "library.json")
	if err := os.WriteFile(legacyPath, data, 0o644); err != nil {
		t.Fatal(err)
	}
	repo := New(filepath.Join(appData, "Lumen"))
	loaded, err := repo.Load()
	if err != nil {
		t.Fatal(err)
	}
	if len(loaded.Vocabulary) != 1 || loaded.Vocabulary[0].Text != "legacy" {
		t.Fatalf("legacy data missing: %+v", loaded.Vocabulary)
	}
	backups, _ := filepath.Glob(legacyPath + ".lumen-migration-*")
	if len(backups) != 1 {
		t.Fatalf("legacy backups = %d", len(backups))
	}
}
