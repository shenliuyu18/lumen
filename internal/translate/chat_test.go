package translate

import (
	"strings"
	"testing"

	"lumen/internal/model"
)

func TestBuildChatRequestReusesTranslationEndpoint(t *testing.T) {
	settings := model.TranslationSettings{Provider: "ai", AIBaseURL: "https://api.deepseek.com/v1/", AIModel: "deepseek-chat"}
	if _, err := BuildChatRequest(settings, "", nil); err == nil {
		t.Fatal("expected missing key error")
	}
	request, err := BuildChatRequest(settings, "sk-test", []ChatMessage{
		{Role: "system", Content: "双语精读老师"},
		{Role: "user", Content: "这句话为什么用 would？"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if request.URL != "https://api.deepseek.com/v1/chat/completions" {
		t.Fatalf("url = %s", request.URL)
	}
	if request.Headers["Authorization"] != "Bearer sk-test" {
		t.Fatalf("headers = %+v", request.Headers)
	}
	if !strings.Contains(string(request.Body), `"model":"deepseek-chat"`) || !strings.Contains(string(request.Body), `"stream":false`) {
		t.Fatalf("body = %s", request.Body)
	}
	for _, provider := range []string{"microsoft-web", "deepl"} {
		if _, err := BuildChatRequest(model.TranslationSettings{Provider: provider}, "key", []ChatMessage{{Role: "user", Content: "hi"}}); err == nil {
			t.Fatalf("%s should not answer questions", provider)
		} else if !strings.Contains(err.Error(), "偏好设置") {
			t.Fatalf("err = %v, want setup guidance", err)
		}
	}
}

func TestParseChatResponse(t *testing.T) {
	answer, err := ParseChatResponse([]byte(`{"choices":[{"message":{"content":" 因为这里是虚拟语气。 "}}]}`))
	if err != nil || answer != "因为这里是虚拟语气。" {
		t.Fatalf("answer = %q err = %v", answer, err)
	}
	if _, err := ParseChatResponse([]byte(`{"error":{"message":"余额不足"}}`)); err == nil || err.Error() != "余额不足" {
		t.Fatalf("err = %v", err)
	}
	if _, err := ParseChatResponse([]byte(`{"choices":[{"message":{"content":"半句"},"finish_reason":"length"}]}`)); err == nil || !strings.Contains(err.Error(), "输出上限") {
		t.Fatalf("err = %v, want the truncation notice", err)
	}
	if _, err := ParseChatResponse([]byte(`{"choices":[]}`)); err == nil {
		t.Fatal("empty choices should fail")
	}
}
