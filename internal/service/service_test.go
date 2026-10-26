package service

import "testing"

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

func TestDeepSeekConfigStoresKeyStatus(t *testing.T) {
	t.Setenv("APPDATA", t.TempDir())
	svc, err := New()
	if err != nil {
		t.Fatal(err)
	}
	state, err := svc.DeepSeekConfig("sk-test")
	if err != nil {
		t.Fatal(err)
	}
	if !state.KeyStatus["deepseek"] {
		t.Fatal("deepseek key status missing")
	}
}
