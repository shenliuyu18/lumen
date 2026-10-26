package plugins

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"lumen/internal/model"
)

func testManager(t *testing.T) *Manager {
	t.Helper()
	manager, err := New(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	return manager
}

func crawlerPlugin(id string, sources int) Plugin {
	plugin := Plugin{
		Format: 1, ID: id, Name: "Test Crawler", Version: "1.0.0", Type: "crawler",
	}
	for index := 0; index < sources; index++ {
		plugin.Sources = append(plugin.Sources, model.Source{
			ID: fmt.Sprintf("%s-source-%d", id, index), Name: "Test", URL: "https://example.com",
			Category: "新闻", Region: "全球", Reach: 3,
		})
	}
	return plugin
}

func TestNoBuiltinPlugins(t *testing.T) {
	manager := testManager(t)
	if infos := manager.List(nil); len(infos) != 0 {
		t.Fatalf("expected no bundled plugins, got %d", len(infos))
	}
	if sources := manager.Sources(nil); len(sources) != 0 {
		t.Fatalf("expected no sources before install, got %d", len(sources))
	}
	if spec := manager.NewsroomSpec(nil); spec != nil {
		t.Fatal("expected no newsroom rule before install")
	}
	if script := manager.AdScript(nil); script != "" {
		t.Fatal("expected no adblock script before install")
	}
}

func TestInstallZipAndRemove(t *testing.T) {
	manager := testManager(t)
	zipPath := filepath.Join(t.TempDir(), "plugin.zip")
	writeZip(t, zipPath, map[string]string{
		"plugin.json": `{"format":1,"id":"test-crawler","name":"Test","version":"1.0.0","type":"crawler","newsroom":{"default":{"denyTitles":["read more"]}},"sources":[{"id":"test-source","name":"Test","url":"https://example.com","feed":"https://example.com/rss","category":"新闻","region":"全球","reach":3}]}`,
	})
	if _, err := manager.Install(zipPath); err != nil {
		t.Fatal(err)
	}
	if len(manager.Sources(nil)) != 1 || manager.Sources(nil)[0].ID != "test-source" {
		t.Fatal("installed source missing")
	}
	if spec := manager.NewsroomSpec(nil); spec == nil || len(spec.Default.DenyTitles) != 1 {
		t.Fatal("installed newsroom rule missing")
	}
	if err := manager.Remove("test-crawler"); err != nil {
		t.Fatal(err)
	}
	if len(manager.List(nil)) != 0 || len(manager.Sources(nil)) != 0 {
		t.Fatal("plugin was not removed")
	}
}

func TestInstallDeclarativeJS(t *testing.T) {
	manager := testManager(t)
	path := filepath.Join(t.TempDir(), "plugin.js")
	content := `// BOM test
module.exports = {
  "format": 1,
  "id": "js-crawler",
  "name": "JS Crawler",
  "version": "1.0.0",
  "type": "crawler",
  "sources": [{"id": "js-source", "name": "JS News", "url": "https://example.com"}]
};`
	if err := os.WriteFile(path, []byte("\ufeff"+content), 0o644); err != nil {
		t.Fatal(err)
	}
	info, err := manager.Install(path)
	if err != nil {
		t.Fatal(err)
	}
	if info.ID != "js-crawler" || info.SourceCount != 1 {
		t.Fatalf("unexpected info: %+v", info)
	}
	if len(manager.Sources(nil)) != 1 || manager.Sources(nil)[0].ID != "js-source" {
		t.Fatal("JS plugin source missing")
	}
	if _, err := os.Stat(filepath.Join(manager.Dir(), "js-crawler", "plugin.json")); err != nil {
		t.Fatal("JS plugin manifest was not materialized")
	}
}

func TestInstallJSWrappersAndAdScript(t *testing.T) {
	manager := testManager(t)
	wrappers := []string{
		"export default %s;",
		"window.__LUMEN_PLUGIN__ = %s;",
		"globalThis.__LUMEN_PLUGIN__ = %s;",
	}
	for index, wrapper := range wrappers {
		plugin := Plugin{
			Format: 1, ID: fmt.Sprintf("js-adblock-%d", index), Name: "JS Adblock", Version: "1.0.0", Type: "adblock",
			Rules: &AdRules{
				Global:  AdRuleSet{Selectors: []string{"[data-ad]"}, TextPatterns: []string{"advertisement"}},
				Domains: map[string]AdRuleSet{"example.com": {Selectors: []string{"#ad"}}},
			},
		}
		encoded, err := json.Marshal(plugin)
		if err != nil {
			t.Fatal(err)
		}
		path := filepath.Join(t.TempDir(), fmt.Sprintf("adblock-%d.js", index))
		if err := os.WriteFile(path, []byte(fmt.Sprintf(wrapper, string(encoded))), 0o644); err != nil {
			t.Fatal(err)
		}
		if _, err := manager.Install(path); err != nil {
			t.Fatal(err)
		}
	}
	script := manager.AdScript(nil)
	for _, want := range []string{"__LUMEN_AD_RULES__", "[data-ad]", "example.com", "__lumenAdblockInstalled"} {
		if !strings.Contains(script, want) {
			t.Fatalf("ad script missing %q", want)
		}
	}
}

func TestJSInstallRejectsInvalid(t *testing.T) {
	manager := testManager(t)
	cases := []string{
		"console.log('no plugin');",
		`module.exports = {"format":1,"id":"../bad","name":"Bad","type":"crawler","sources":[{"id":"s","name":"S","url":"https://e.com"}]};`,
		`module.exports = {"format":1,"id":"empty","name":"Empty","type":"crawler"};`,
		`module.exports = {notJson: true};`,
	}
	for _, content := range cases {
		path := filepath.Join(t.TempDir(), "bad.js")
		if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
		if _, err := manager.Install(path); err == nil {
			t.Fatalf("expected invalid JS plugin to fail: %s", content)
		}
	}
}

func TestInstallRejectsUnsafeIDAndTraversal(t *testing.T) {
	manager := testManager(t)
	unsafe := filepath.Join(t.TempDir(), "unsafe.zip")
	writeZip(t, unsafe, map[string]string{
		"plugin.json": `{"format":1,"id":"../escape","name":"Bad","type":"crawler","sources":[{"id":"s","name":"S","url":"https://example.com"}]}`,
	})
	if _, err := manager.Install(unsafe); err == nil {
		t.Fatal("expected unsafe id to be rejected")
	}
	traversal := filepath.Join(t.TempDir(), "traversal.zip")
	writeZip(t, traversal, map[string]string{
		"pkg/plugin.json":    `{"format":1,"id":"safe-id","name":"Safe","type":"crawler","sources":[{"id":"s","name":"S","url":"https://example.com"}]}`,
		"pkg/../../outside":  "bad",
		"pkg/C:/evil.txt":    "bad",
		"pkg/nested/../../x": "bad",
	})
	if _, err := manager.Install(traversal); err != nil {
		t.Fatal(err)
	}
	pluginDir := filepath.Join(manager.Dir(), "safe-id")
	if _, err := os.Stat(pluginDir); err != nil {
		t.Fatal("valid plugin was not installed")
	}
	if _, err := os.Stat(filepath.Join(filepath.Dir(manager.Dir()), "outside")); !os.IsNotExist(err) {
		t.Fatal("zip entry escaped plugin directory")
	}
}

func TestDisabledPlugins(t *testing.T) {
	manager := testManager(t)
	plugin := crawlerPlugin("disabled-crawler", 1)
	encoded, _ := json.Marshal(plugin)
	path := filepath.Join(t.TempDir(), "crawler.js")
	if err := os.WriteFile(path, append([]byte("module.exports = "), append(encoded, ';')...), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := manager.Install(path); err != nil {
		t.Fatal(err)
	}
	if len(manager.Sources(nil)) != 1 {
		t.Fatal("source missing before disable")
	}
	if len(manager.Sources([]string{"disabled-crawler"})) != 0 {
		t.Fatal("disabled crawler should remove sources")
	}
	if infos := manager.List([]string{"disabled-crawler"}); len(infos) != 1 || infos[0].Enabled {
		t.Fatal("plugin should be listed as disabled")
	}
}

func TestDecodePluginValidation(t *testing.T) {
	cases := []string{
		`{"format":2,"id":"x","name":"X","type":"crawler","sources":[{"id":"s","name":"S","url":"https://e.com"}]}`,
		`{"format":1,"id":"x y","name":"X","type":"crawler","sources":[{"id":"s","name":"S","url":"https://e.com"}]}`,
		`{"format":1,"id":"x","name":"X","type":"unknown"}`,
		`{"format":1,"id":"x","name":"X","type":"crawler"}`,
		`{"format":1,"id":"x","name":"X","type":"adblock"}`,
	}
	for _, text := range cases {
		if _, err := decodePlugin([]byte(text)); err == nil {
			t.Fatalf("expected invalid plugin: %s", text)
		}
	}
	valid := crawlerPlugin("valid-id", 1)
	encoded, _ := json.Marshal(valid)
	if _, err := decodePlugin(encoded); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func writeZip(t *testing.T, path string, files map[string]string) {
	t.Helper()
	handle, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	writer := zip.NewWriter(handle)
	for name, content := range files {
		entry, err := writer.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write([]byte(content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := handle.Close(); err != nil {
		t.Fatal(err)
	}
}
