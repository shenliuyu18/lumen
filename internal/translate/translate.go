package translate

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html"
	"net/http"
	"net/url"
	"strings"
	"time"

	"lumen/internal/model"
)

var Providers = []string{"microsoft-web", "microsoft", "deepl", "ai", "mymemory"}

// Edge 浏览器内置翻译使用的公开接口：无需密钥，但需要浏览器风格的请求头。
const (
	edgeWebEndpoint = "https://edge.microsoft.com/translate/translatetext?api-version=3.0&from=en&to=zh-Hans"
	edgeUserAgent   = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
)

type Request struct {
	URL     string
	Method  string
	Headers map[string]string
	Body    []byte
}

func CacheKey(provider, text, context string) string {
	payload, _ := json.Marshal([]string{provider, text, context})
	sum := sha256.Sum256(payload)
	return hex.EncodeToString(sum[:])
}

func BuildRequest(settings model.TranslationSettings, key, text, context string) (Request, error) {
	switch settings.Provider {
	case "microsoft-web":
		body, _ := json.Marshal([]string{text})
		return Request{
			URL:    edgeWebEndpoint,
			Method: http.MethodPost,
			Headers: map[string]string{
				"Content-Type": "application/json",
				"User-Agent":   edgeUserAgent,
				"Origin":       "https://www.microsoft.com",
				"Referer":      "https://www.microsoft.com/zh-cn/translator/",
			},
			Body: body,
		}, nil
	case "microsoft":
		if key == "" {
			return Request{}, fmt.Errorf("请在偏好设置中填写 Microsoft Translator API 密钥，或选择无需密钥的微软官方网页。")
		}
		body, _ := json.Marshal([]map[string]string{{"Text": text}})
		headers := map[string]string{
			"Content-Type":              "application/json",
			"Ocp-Apim-Subscription-Key": key,
		}
		if settings.Region != "" {
			headers["Ocp-Apim-Subscription-Region"] = settings.Region
		}
		return Request{
			URL:     "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=en&to=zh-Hans",
			Method:  http.MethodPost,
			Headers: headers,
			Body:    body,
		}, nil
	case "deepl":
		if key == "" {
			return Request{}, fmt.Errorf("请在偏好设置中填写 DeepL API 密钥。")
		}
		host := "api-free.deepl.com"
		if settings.DeeplPlan == "pro" {
			host = "api.deepl.com"
		}
		payload := map[string]any{
			"text":                []string{text},
			"source_lang":         "EN",
			"target_lang":         "ZH-HANS",
			"preserve_formatting": true,
		}
		if context != "" {
			payload["context"] = context
		}
		body, _ := json.Marshal(payload)
		return Request{
			URL:    "https://" + host + "/v2/translate",
			Method: http.MethodPost,
			Headers: map[string]string{
				"Content-Type":  "application/json",
				"Authorization": "DeepL-Auth-Key " + key,
			},
			Body: body,
		}, nil
	case "ai":
		if key == "" {
			return Request{}, fmt.Errorf("请在偏好设置中填写大模型服务的 API 密钥。")
		}
		base := strings.TrimSpace(settings.AIBaseURL)
		if base == "" {
			base = "https://api.openai.com/v1"
		}
		modelName := strings.TrimSpace(settings.AIModel)
		if modelName == "" {
			modelName = "gpt-4o-mini"
		}
		system := "你是严谨的英译中译者。只输出中文译文，不要解释、不要加引号或列表符号；保留专有名词、数字与代码原样。"
		user := text
		if context != "" {
			user = "上下文（仅供参考，不要翻译它）：\n" + context + "\n\n需要翻译的段落：\n" + text
		}
		body, _ := json.Marshal(map[string]any{
			"model":       modelName,
			"temperature": 0.2,
			"messages": []map[string]string{
				{"role": "system", "content": system},
				{"role": "user", "content": user},
			},
		})
		return Request{
			URL:    strings.TrimSuffix(base, "/") + "/chat/completions",
			Method: http.MethodPost,
			Headers: map[string]string{
				"Content-Type":  "application/json",
				"Authorization": "Bearer " + key,
			},
			Body: body,
		}, nil
	case "mymemory":
		if len([]byte(text)) > 500 {
			return Request{}, fmt.Errorf("备用免费服务每次最多 500 字节。请缩短选文或切换 Microsoft / DeepL。")
		}
		return Request{
			URL:    "https://api.mymemory.translated.net/get?q=" + url.QueryEscape(text) + "&langpair=en%7Czh-CN",
			Method: http.MethodGet,
		}, nil
	default:
		return Request{}, fmt.Errorf("请选择 API 翻译服务后再使用内嵌译文。")
	}
}

func ParseResponse(provider string, data []byte) (model.Translation, error) {
	var text string
	switch provider {
	case "microsoft", "microsoft-web":
		var payload []struct {
			Translations []struct {
				Text string `json:"text"`
			} `json:"translations"`
		}
		if err := json.Unmarshal(data, &payload); err == nil && len(payload) > 0 && len(payload[0].Translations) > 0 {
			text = payload[0].Translations[0].Text
		}
	case "deepl":
		var payload struct {
			Translations []struct {
				Text string `json:"text"`
			} `json:"translations"`
		}
		if err := json.Unmarshal(data, &payload); err == nil && len(payload.Translations) > 0 {
			text = payload.Translations[0].Text
		}
	case "ai":
		var payload struct {
			Choices []struct {
				Message struct {
					Content string `json:"content"`
				} `json:"message"`
			} `json:"choices"`
			Error *struct {
				Message string `json:"message"`
			} `json:"error"`
		}
		if err := json.Unmarshal(data, &payload); err == nil {
			if payload.Error != nil && payload.Error.Message != "" {
				return model.Translation{}, fmt.Errorf("大模型服务：%s", payload.Error.Message)
			}
			if len(payload.Choices) > 0 {
				text = strings.TrimSpace(payload.Choices[0].Message.Content)
			}
		}
	case "mymemory":
		var payload struct {
			ResponseStatus int  `json:"responseStatus"`
			QuotaFinished  bool `json:"quotaFinished"`
			ResponseData   struct {
				TranslatedText string `json:"translatedText"`
			} `json:"responseData"`
			ResponseDetails string `json:"responseDetails"`
		}
		if err := json.Unmarshal(data, &payload); err == nil {
			if payload.ResponseStatus != 200 || payload.QuotaFinished {
				return model.Translation{}, fmt.Errorf("%s", firstNonEmpty(payload.ResponseDetails, "备用翻译服务达到配额"))
			}
			text = payload.ResponseData.TranslatedText
		}
	}
	if text == "" {
		return model.Translation{}, fmt.Errorf("翻译服务未返回有效译文")
	}
	names := map[string]string{"microsoft-web": "Microsoft Translator 官方网页", "microsoft": "Microsoft Translator", "deepl": "DeepL", "ai": "大模型翻译", "mymemory": "MyMemory · 备用"}
	return model.Translation{Text: html.UnescapeString(text), Provider: names[provider], At: time.Now().UnixMilli()}, nil
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}

// ChatMessage 是提问小窗使用的对话消息；接口地址、模型与密钥沿用翻译服务配置。
type ChatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

func BuildChatRequest(settings model.TranslationSettings, key string, messages []ChatMessage) (Request, error) {
	if settings.Provider != "ai" {
		return Request{}, fmt.Errorf("提问沿用翻译接口配置：请在「偏好设置 → 翻译服务」选择「大模型 · OpenAI 兼容」，填写接口地址、模型与密钥（DeepSeek 官方填 https://api.deepseek.com/v1 与 deepseek-chat）。")
	}
	if strings.TrimSpace(key) == "" {
		return Request{}, fmt.Errorf("请先在「偏好设置 → 翻译服务」填写大模型的 API 密钥。")
	}
	if len(messages) == 0 {
		return Request{}, fmt.Errorf("没有需要发送的问题。")
	}
	base := strings.TrimSpace(settings.AIBaseURL)
	if base == "" {
		base = "https://api.openai.com/v1"
	}
	modelName := strings.TrimSpace(settings.AIModel)
	if modelName == "" {
		modelName = "gpt-4o-mini"
	}
	body, _ := json.Marshal(map[string]any{
		"model":       modelName,
		"temperature": 0.3,
		"stream":      false,
		"messages":    messages,
	})
	return Request{
		URL:    strings.TrimSuffix(base, "/") + "/chat/completions",
		Method: http.MethodPost,
		Headers: map[string]string{
			"Content-Type":  "application/json",
			"Authorization": "Bearer " + key,
		},
		Body: body,
	}, nil
}

func ParseChatResponse(data []byte) (string, error) {
	var payload struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
			FinishReason string `json:"finish_reason"`
		} `json:"choices"`
		Error *struct {
			Message string `json:"message"`
		} `json:"error"`
	}
	if err := json.Unmarshal(data, &payload); err != nil {
		return "", fmt.Errorf("无法解析大模型响应")
	}
	if payload.Error != nil && strings.TrimSpace(payload.Error.Message) != "" {
		return "", fmt.Errorf("%s", payload.Error.Message)
	}
	if len(payload.Choices) == 0 {
		return "", fmt.Errorf("大模型未返回有效回答")
	}
	content := strings.TrimSpace(payload.Choices[0].Message.Content)
	if content == "" {
		return "", fmt.Errorf("大模型未返回有效回答")
	}
	if payload.Choices[0].FinishReason == "length" {
		return content, fmt.Errorf("回答达到模型输出上限，请缩短问题或分次提问")
	}
	return content, nil
}

func EncodeRequest(request Request) (*http.Request, error) {
	req, err := http.NewRequest(request.Method, request.URL, bytes.NewReader(request.Body))
	if err != nil {
		return nil, err
	}
	for key, value := range request.Headers {
		req.Header.Set(key, value)
	}
	return req, nil
}
