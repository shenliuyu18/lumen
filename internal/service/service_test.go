package service

import (
	"strings"
	"testing"
)

func TestFreshInstallHasNoBundledPlugins(t *testing.T) {
	t.Setenv("APPDATA", t.TempDir())
	svc, err := New()
	if err != nil {
		t.Fatal(err)
	}
	state := svc.State()
	if len(state.Plugins) != 0 {
		t.Fatalf("plugins = %d, want 0 before import", len(state.Plugins))
	}
	if len(state.Sources) != 0 {
		t.Fatalf("sources = %d, want 0 before crawler import", len(state.Sources))
	}
	if len(state.Articles) == 0 {
		t.Fatal("seed articles missing")
	}
}

func TestGuanduSettingsStartFromExtensionDefaults(t *testing.T) {
	t.Setenv("APPDATA", t.TempDir())
	svc, err := New()
	if err != nil {
		t.Fatal(err)
	}
	state := svc.State()
	if state.Settings.Display.CornerRadius != 4 || state.Settings.Display.Palette != "original" {
		t.Fatalf("display = %+v, want guandu defaults", state.Settings.Display)
	}
	if state.Settings.ChatWindow.Width != 420 || state.Settings.ChatWindow.Height != 600 || state.Settings.ChatWindow.Side != "right" {
		t.Fatalf("chat window = %+v, want 420x600 right", state.Settings.ChatWindow)
	}
}

func TestGuanduSettingsPatchAndClamp(t *testing.T) {
	t.Setenv("APPDATA", t.TempDir())
	svc, err := New()
	if err != nil {
		t.Fatal(err)
	}
	state := svc.Settings(map[string]any{"display": map[string]any{
		"palette": "sky", "cornerRadius": 99.0, "fontPreset": "unknown", "borderColor": "#BAD",
	}})
	display := state.Settings.Display
	if display.Palette != "sky" {
		t.Fatalf("palette = %s, want sky", display.Palette)
	}
	if display.CornerRadius != 24 {
		t.Fatalf("cornerRadius = %v, want clamped to 24", display.CornerRadius)
	}
	if display.FontPreset != "reading" {
		t.Fatalf("fontPreset = %s, want the default back", display.FontPreset)
	}
	if display.Border != "#dce3d0" {
		t.Fatalf("borderColor = %s, want the default hex back", display.Border)
	}
	if display.Background != "#f1f2e7" {
		t.Fatalf("backgroundColor = %s, want it kept until a custom palette is used", display.Background)
	}
	state = svc.Settings(map[string]any{"chatWindow": map[string]any{"width": 2000.0, "height": 120.0, "side": "middle"}})
	window := state.Settings.ChatWindow
	if window.Width != 900 || window.Height != 360 || window.Side != "right" || !window.Enabled {
		t.Fatalf("chat window = %+v, want clamped 900x360 right", window)
	}
	state = svc.Settings(map[string]any{"chatWindow": map[string]any{"side": "left", "enabled": false}})
	if state.Settings.ChatWindow.Side != "left" || state.Settings.ChatWindow.Enabled {
		t.Fatalf("chat window = %+v, want the ball hidden on the left", state.Settings.ChatWindow)
	}
}

func TestGuanduAskFollowsTranslationProvider(t *testing.T) {
	t.Setenv("APPDATA", t.TempDir())
	svc, err := New()
	if err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Ask("session", map[string]any{"question": "这句话为什么用 would？"}); err == nil {
		t.Fatal("ask should require the chat-capable provider")
	} else if !strings.Contains(err.Error(), "翻译接口") {
		t.Fatalf("err = %v, want guidance about the translation provider", err)
	}
	if _, err := svc.Ask("session", map[string]any{"question": strings.Repeat("问", 4001)}); err == nil {
		t.Fatal("oversized question should be rejected")
	}
	if _, err := svc.Ask("session", map[string]any{
		"question": "解释选文",
		"context":  map[string]any{"text": "would you have gone", "title": "A", "url": "ftp://example.com", "kind": "selection", "truncated": false},
	}); err == nil || !strings.Contains(err.Error(), "来源地址") {
		t.Fatalf("err = %v, want the context URL rejected first", err)
	}
	if _, err := svc.Ask("session", map[string]any{
		"question": "解释选文",
		"history":  []any{map[string]any{"role": "user", "content": strings.Repeat("长", 12001)}},
	}); err == nil || !strings.Contains(err.Error(), "历史对话过长") {
		t.Fatalf("err = %v, want the history limit rejected", err)
	}
}
