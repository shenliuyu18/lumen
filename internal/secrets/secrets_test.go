package secrets

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestDPAPIRoundTrip(t *testing.T) {
	path := filepath.Join(t.TempDir(), "keys.bin")
	manager := New(path)
	if err := manager.Set("deepl", "secret-value"); err != nil {
		t.Fatal(err)
	}
	if got := manager.Get("deepl"); got != "secret-value" {
		t.Fatalf("get = %q", got)
	}
	status := manager.Status()
	if !status["deepl"] || status["microsoft"] {
		t.Fatalf("status = %v", status)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(data), "secret-value") {
		t.Fatal("key stored in plaintext")
	}
	if err := manager.Remove("deepl"); err != nil {
		t.Fatal(err)
	}
	if got := manager.Get("deepl"); got != "" {
		t.Fatalf("removed key still readable: %q", got)
	}
}
