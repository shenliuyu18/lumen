package translate

import (
	"encoding/json"
	"strings"
	"testing"

	"lumen/internal/model"
)

func TestMicrosoftRequest(t *testing.T) {
	request, err := BuildRequest(model.TranslationSettings{Provider: "microsoft", Region: "eastasia"}, "test", "Evidence", "")
	if err != nil {
		t.Fatal(err)
	}
	if request.URL != "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=en&to=zh-Hans" {
		t.Fatalf("url = %s", request.URL)
	}
	if request.Headers["Ocp-Apim-Subscription-Key"] != "test" {
		t.Fatal("missing key header")
	}
	var body []map[string]string
	if err := json.Unmarshal(request.Body, &body); err != nil || body[0]["Text"] != "Evidence" {
		t.Fatalf("unexpected body: %s", request.Body)
	}
	result, err := ParseResponse("microsoft", []byte(`[{"translations":[{"text":"证据"}]}]`))
	if err != nil || result.Text != "证据" {
		t.Fatalf("parse result: %+v %v", result, err)
	}
}

func TestMicrosoftWebRequestIsKeyless(t *testing.T) {
	request, err := BuildRequest(model.TranslationSettings{Provider: "microsoft-web"}, "", "Evidence", "")
	if err != nil {
		t.Fatal(err)
	}
	if request.URL != "https://edge.microsoft.com/translate/translatetext?api-version=3.0&from=en&to=zh-Hans" {
		t.Fatalf("url = %s", request.URL)
	}
	if _, hasKey := request.Headers["Ocp-Apim-Subscription-Key"]; hasKey {
		t.Fatal("网页接口不应携带密钥")
	}
	if request.Headers["User-Agent"] == "" || request.Headers["Referer"] == "" {
		t.Fatalf("missing browser headers: %v", request.Headers)
	}
	var body []string
	if err := json.Unmarshal(request.Body, &body); err != nil || body[0] != "Evidence" {
		t.Fatalf("unexpected body: %s", request.Body)
	}
	result, err := ParseResponse("microsoft-web", []byte(`[{"translations":[{"text":"证据","to":"zh-Hans"}]}]`))
	if err != nil || result.Text != "证据" || result.Provider != "Microsoft Translator 官方网页" {
		t.Fatalf("parse result: %+v %v", result, err)
	}
	if _, err := ParseResponse("microsoft-web", []byte(`[{"translations":[]}]`)); err == nil {
		t.Fatal("empty web response accepted")
	}
}

func TestDeepLRequest(t *testing.T) {
	request, err := BuildRequest(model.TranslationSettings{Provider: "deepl", DeeplPlan: "pro"}, "test", "bank", "river")
	if err != nil {
		t.Fatal(err)
	}
	if request.URL != "https://api.deepl.com/v2/translate" {
		t.Fatalf("url = %s", request.URL)
	}
	var body map[string]any
	if err := json.Unmarshal(request.Body, &body); err != nil {
		t.Fatal(err)
	}
	if body["context"] != "river" || body["target_lang"] != "ZH-HANS" {
		t.Fatalf("unexpected body: %v", body)
	}
}

func TestValidationAndCacheKey(t *testing.T) {
	if _, err := BuildRequest(model.TranslationSettings{Provider: "microsoft"}, "", "x", ""); err == nil {
		t.Fatal("missing key accepted")
	}
	long := make([]byte, 501)
	for i := range long {
		long[i] = 'x'
	}
	if _, err := BuildRequest(model.TranslationSettings{Provider: "mymemory"}, "", string(long), ""); err == nil {
		t.Fatal("oversized mymemory accepted")
	}
	if _, err := ParseResponse("deepl", []byte(`{}`)); err == nil {
		t.Fatal("empty deepl response accepted")
	}
	if CacheKey("deepl", "bank", "river") == CacheKey("deepl", "bank", "finance") {
		t.Fatal("cache key ignores context")
	}
}

func TestAITranslationRequestIsOpenAICompatible(t *testing.T) {
	settings := model.TranslationSettings{
		Provider:  "ai",
		AIBaseURL: "https://api.example.com/v1/",
		AIModel:   "qwen-plus",
	}
	request, err := BuildRequest(settings, "sk-test", "Hello world.", "Previous sentence.")
	if err != nil {
		t.Fatalf("BuildRequest: %v", err)
	}
	if request.URL != "https://api.example.com/v1/chat/completions" {
		t.Fatalf("unexpected URL %q", request.URL)
	}
	if got := request.Headers["Authorization"]; got != "Bearer sk-test" {
		t.Fatalf("unexpected authorization %q", got)
	}
	var payload struct {
		Model    string `json:"model"`
		Messages []struct {
			Role    string `json:"role"`
			Content string `json:"content"`
		} `json:"messages"`
	}
	if err := json.Unmarshal(request.Body, &payload); err != nil {
		t.Fatalf("body: %v", err)
	}
	if payload.Model != "qwen-plus" || len(payload.Messages) != 2 {
		t.Fatalf("unexpected payload %+v", payload)
	}
	if !strings.Contains(payload.Messages[1].Content, "Previous sentence.") {
		t.Fatalf("context missing from user message: %q", payload.Messages[1].Content)
	}

	translation, err := ParseResponse("ai", []byte(`{"choices":[{"message":{"content":" 你好，世界。 "}}]}`))
	if err != nil || translation.Text != "你好，世界。" || translation.Provider != "大模型翻译" {
		t.Fatalf("unexpected translation %+v err=%v", translation, err)
	}
	if _, err := BuildRequest(model.TranslationSettings{Provider: "ai", AIBaseURL: "https://x/v1"}, "", "hi", ""); err == nil {
		t.Fatal("expected missing key error")
	}
}
