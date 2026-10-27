package access

import (
	"context"
	"strings"
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

// 文章里重复出现的短句必须各自按序号授权，不能命中第一次出现的那一段。
func TestValidateAllowsRepeatedParagraphs(t *testing.T) {
	paragraphs := []Paragraph{
		{Text: "By"},
		{Text: "Casey Crownhart"},
		{Text: "By"},
		{Text: "Amy Nordrum"},
	}
	service := New()
	service.current = &Article{Title: "Test", URL: "https://example.com/a", Paragraphs: paragraphs}
	result, err := service.Authorize(context.Background(), "microsoft-web", nil)
	if err != nil {
		t.Fatal(err)
	}
	token := result["token"].(string)
	for index, paragraph := range paragraphs {
		start, end := index-1, index+2
		if start < 0 {
			start = 0
		}
		if end > len(paragraphs) {
			end = len(paragraphs)
		}
		joined := make([]string, 0, end-start)
		for _, value := range paragraphs[start:end] {
			joined = append(joined, value.Text)
		}
		payload := map[string]any{
			"authorization": token,
			"text":          paragraph.Text,
			"context":       strings.Join(joined, "\n"),
			"index":         float64(index),
		}
		if err := service.Validate(payload, "microsoft-web"); err != nil {
			t.Fatalf("paragraph %d (%q) rejected: %v", index, paragraph.Text, err)
		}
	}
	if err := service.Validate(map[string]any{"authorization": token, "text": "By", "index": float64(1)}, "microsoft-web"); err == nil {
		t.Fatal("text that does not belong to the index should be rejected")
	}
	if err := service.Validate(map[string]any{"authorization": token, "text": "Amy Nordrum", "index": float64(9)}, "microsoft-web"); err == nil {
		t.Fatal("out-of-range index should be rejected")
	}
	if err := service.Validate(map[string]any{"authorization": token, "text": "By"}, "microsoft-web"); err == nil {
		t.Fatal("missing index should be rejected")
	}
}
