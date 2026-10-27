//go:build lumen_test

package main

import (
	"encoding/json"
	"fmt"
	wailsRuntime "github.com/wailsapp/wails/v2/pkg/runtime"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync"
	"time"
)

var desktopReportMu sync.Mutex
var desktopReport = map[string]any{}

func (a *App) TestReport(name string, result any) {
	desktopReportMu.Lock()
	desktopReport[name] = result
	desktopReportMu.Unlock()
}
func runDesktopChecks(a *App) {
	go func() {
		out := os.Getenv("LUMEN_TEST_OUTPUT")
		if out == "" {
			return
		}
		os.MkdirAll(out, 0755)
		defer func() {
			if value := recover(); value != nil {
				a.TestReport("panic", fmt.Sprint(value))
			}
			desktopReportMu.Lock()
			data, _ := json.MarshalIndent(desktopReport, "", "  ")
			desktopReportMu.Unlock()
			os.WriteFile(filepath.Join(out, "desktop.json"), data, 0644)
			wailsRuntime.Quit(a.ctx)
		}()
		marker := filepath.Join(out, "restart-parent.json")
		if saved, err := os.ReadFile(marker); err == nil {
			oldReport, _ := os.ReadFile(filepath.Join(out, "desktop.json"))
			_ = json.Unmarshal(oldReport, &desktopReport)
			state := a.svc.State()
			vocabOK := false
			disabledOK := false
			for _, v := range state.Vocabulary {
				if v.Text == "Lumen restart fixture" {
					vocabOK = true
				}
			}
			for _, id := range state.Settings.DisabledPlugins {
				if id == "lumen-adblock" {
					disabledOK = true
				}
			}
			a.TestReport("restart-roundtrip", map[string]any{"parent": string(saved), "newPID": os.Getpid(), "vocabularyPreserved": vocabOK, "pluginPreferencePreserved": disabledOK, "sources": len(state.Sources)})
			return
		}
		for _, path := range []string{os.Getenv("LUMEN_TEST_CRAWLER"), os.Getenv("LUMEN_TEST_ADBLOCK"), os.Getenv("LUMEN_TEST_BROWSER")} {
			info, _, err := a.svc.InstallPlugin(path)
			if err != nil {
				panic(err)
			}
			a.TestReport("installed-"+info.Type, map[string]any{"id": info.ID, "name": info.Name, "version": info.Version})
		}
		a.syncBrowserPlugins()
		deadline := time.Now().Add(20 * time.Second)
		for time.Now().Before(deadline) {
			statuses := a.host.ExtensionStatus()
			ready := len(statuses) > 0
			for _, s := range statuses {
				if s.State == "加载中" {
					ready = false
				}
			}
			if ready {
				break
			}
			time.Sleep(150 * time.Millisecond)
		}
		a.TestReport("native-extensions", a.host.ExtensionStatus())
		for id, status := range a.host.ExtensionStatus() {
			if status.State != "运行中" {
				continue
			}
			target := a.host.ExtensionOptionsURL(id)
			a.host.Navigate(target)
			time.Sleep(1000 * time.Millisecond)
			var value string
			var err error
			for attempt := 0; attempt < 15; attempt++ {
				value, err = a.host.EvalResult(`return (async()=>({settingsLoaded:location.protocol==='chrome-extension:',rules:(await chrome.declarativeNetRequest.getSessionRules()).length}))()`, 4*time.Second)
				var observed struct {
					Rules int `json:"rules"`
				}
				_ = json.Unmarshal([]byte(value), &observed)
				if err == nil && observed.Rules > 0 {
					break
				}
				time.Sleep(650 * time.Millisecond)
			}
			a.TestReport("bypass-background", map[string]any{"value": value, "error": fmt.Sprint(err)})
		}
		// Test only a local synthetic page and a new fixture extension in the test profile.
		fixture := filepath.Join(out, "fixture")
		os.MkdirAll(fixture, 0755)
		os.WriteFile(filepath.Join(fixture, "manifest.json"), []byte(`{"manifest_version":3,"name":"Lumen Native Smoke","version":"1.0","content_scripts":[{"matches":["http://127.0.0.1/*"],"js":["content.js"]}]}`), 0644)
		os.WriteFile(filepath.Join(fixture, "content.js"), []byte(`document.documentElement.setAttribute('data-lumen-extension-smoke','running');`), 0644)
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			fmt.Fprint(w, `<!doctype html><html><head><title>Native fixture</title></head><body><article><h1>A native browser extension fixture</h1><p>Reading content and saved learning data stay available.</p></article></body></html>`)
		}))
		defer server.Close()
		info, _, err := a.svc.InstallPlugin(fixture)
		if err != nil {
			panic(err)
		}
		a.syncBrowserPlugins()
		time.Sleep(2200 * time.Millisecond)
		a.host.Navigate(server.URL)
		time.Sleep(1600 * time.Millisecond)
		value, err := a.host.EvalResult(`return document.documentElement.getAttribute('data-lumen-extension-smoke')`, 4*time.Second)
		a.TestReport("content-script", map[string]any{"value": value, "error": fmt.Sprint(err)})
		a.svc.SetPluginEnabled(info.ID, false)
		a.syncBrowserPlugins()
		time.Sleep(1500 * time.Millisecond)
		a.host.Navigate(server.URL + "/disabled")
		time.Sleep(1200 * time.Millisecond)
		value, err = a.host.EvalResult(`return document.documentElement.getAttribute('data-lumen-extension-smoke')`, 4*time.Second)
		a.TestReport("content-script-disabled", map[string]any{"value": value, "error": fmt.Sprint(err)})
		a.svc.RemovePlugin(info.ID)
		a.syncBrowserPlugins()
		// Drive the actual Wails renderer without a remote debugger.
		wailsRuntime.WindowExecJS(a.ctx, `(async()=>{const errors=[];addEventListener('error',e=>errors.push(e.message));try{setState(await api.call('state'));page='plugins';render();await updatePluginRuntime();const cards=document.querySelectorAll('.plugin-card').length;const search=document.getElementById('plugin-search');search.value='Bypass';search.dispatchEvent(new Event('input',{bubbles:true}));const filtered=document.querySelectorAll('.plugin-card').length;document.getElementById('plugin-search').value='';document.getElementById('plugin-search').dispatchEvent(new Event('input',{bubbles:true}));await window.go.main.App.TestReport('renderer',{cards,filtered,overflow:document.documentElement.scrollWidth>innerWidth,restartButton:!!document.querySelector('[data-action="app-restart"]'),errors});}catch(e){await window.go.main.App.TestReport('renderer-error',String(e));}})();`)
		time.Sleep(1800 * time.Millisecond)
		wailsRuntime.WindowSetSize(a.ctx, 1120, 740)
		time.Sleep(700 * time.Millisecond)
		wailsRuntime.WindowExecJS(a.ctx, `window.go.main.App.TestReport('small-window',{width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth})`)
		time.Sleep(500 * time.Millisecond)
		a.TestReport("library", map[string]any{"sources": len(a.svc.Sources()), "plugins": len(a.svc.PluginInfos())})
		fixtureData, _ := json.Marshal(map[string]any{"state": a.svc.State(), "runtime": a.host.ExtensionStatus()})
		os.WriteFile(filepath.Join(out, "ui-fixture.json"), fixtureData, 0644)
		a.svc.VocabAdd(map[string]any{"text": "Lumen restart fixture", "meaning": "isolated test"})
		a.svc.SetPluginEnabled("lumen-adblock", false)
		parent, _ := json.Marshal(map[string]any{"pid": os.Getpid()})
		os.WriteFile(marker, parent, 0644)
		if _, err := a.Restart(); err != nil {
			a.TestReport("restart-error", err.Error())
		}

	}()
}
