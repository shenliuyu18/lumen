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
	"time"

	"lumen/internal/model"
)

var Providers = []string{"microsoft-web", "microsoft", "deepl", "mymemory"}

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
	names := map[string]string{"microsoft-web": "Microsoft Translator 官方网页", "microsoft": "Microsoft Translator", "deepl": "DeepL", "mymemory": "MyMemory · 备用"}
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
