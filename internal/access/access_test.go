package access

import (
	"context"
	"testing"
)

func TestPublicTextMatches(t *testing.T) {
	visible := []Paragraph{{Text: "Visible public paragraph."}}
	if !PublicTextMatches("<p>Visible public paragraph.</p>", visible) {
		t.Fatal("visible paragraph rejected")
	}
	if PublicTextMatches("<p>Visible public paragraph.</p>", append(visible, Paragraph{Text: "Paid subscribers only text."})) {
		t.Fatal("hidden paragraph accepted")
	}
	if PublicTextMatches("<script>Private text hidden in data</script>", []Paragraph{{Text: "Private text hidden in data"}}) {
		t.Fatal("script-only text matched")
	}
	if PublicTextMatches("anything", nil) {
		t.Fatal("empty paragraphs matched")
	}
}

func TestAuthorizeWithoutPromptAllowsUnverified(t *testing.T) {
	service := New()
	service.current = &Article{Title: "Test", URL: "https://example.com/a", Paragraphs: []Paragraph{{Text: "Only one paragraph."}}}
	result, err := service.Authorize(context.Background(), "microsoft-web", nil)
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || result["token"] == "" {
		t.Fatalf("expected authorization token, got %v", result)
	}
}
