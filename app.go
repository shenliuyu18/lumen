package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	wailsRuntime "github.com/wailsapp/wails/v2/pkg/runtime"

	"lumen/internal/access"
	"lumen/internal/extractor"
	"lumen/internal/model"
	"lumen/internal/rank"
	"lumen/internal/service"
	"lumen/internal/webhost"
)

type App struct {
	ctx              context.Context
	host             *webhost.Host
	svc              *service.Service
	access           *access.Service
	urlMu            sync.Mutex
	readerURL        string
	translator       *webhost.Host
	translatorMu     sync.Mutex
	extractor        *webhost.Host
	extractorMu      sync.Mutex
	translateMu      sync.Mutex
	readMu           sync.Mutex
	restartMu        sync.Mutex
	restarting       bool
	readStart        time.Time
	chat             *webhost.Host
	chatMu           sync.Mutex
	chatBounds       map[string]float64
	lowStorage       bool
	tempWebRoot      string
	translatorWindow *webhost.Host
	chatPopup        *webhost.Host
	deepseek         *webhost.Host
	deepseekMu       sync.Mutex
	deepseekBounds   map[string]float64
	deepseekPopup    *webhost.Host
}

func NewApp() *App {
	return &App{access: access.New()}
}

func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	svc, err := service.New()
	if err != nil {
		fmt.Println("[lumen] service:", err)
		return
	}
	svc.SetEmitter(func(event string, data any) {
		wailsRuntime.EventsEmit(ctx, event, data)
	})
	a.svc = svc
}

func (a *App) domReady(ctx context.Context) {
	fmt.Println("[lumen] domReady")
	if a.svc != nil {
		state := a.svc.State()
		a.lowStorage = state.Settings.LowStorage
		if a.lowStorage {
			a.tempWebRoot = filepath.Join(os.TempDir(), fmt.Sprintf("Lumen-%d", os.Getpid()))
			_ = os.MkdirAll(a.tempWebRoot, 0o755)
		}
	}
	script, err := extractor.Script()
	if err != nil {
		fmt.Println("[lumen] extractor:", err)
	}
	a.host = webhost.New(webhost.Options{
		DataPath:        a.webDataPath("reader"),
		ExtractScript:   script,
		OnMessage:       a.handleReaderMessage,
		EnableSelection: true,
		BlockFunc: func(raw string) bool {
			return a.svc != nil && a.svc.BlockURL(raw)
		},
	})
	if err := a.host.Start(15 * time.Second); err != nil {
		fmt.Println("[lumen] webhost:", err)
	}
	a.startAutoRefresh()
}

func (a *App) webDataPath(role string) string {
	if a.tempWebRoot != "" {
		return filepath.Join(a.tempWebRoot, role)
	}
	return filepath.Join(os.Getenv("APPDATA"), "Lumen", "webview2", role)
}

func (a *App) startAutoRefresh() {
	if a.svc == nil || os.Getenv("LUMEN_TEST") == "1" {
		return
	}
	state := a.svc.State()
	if state.Settings.AutoRefresh {
		due := state.LastRefresh == nil || time.Since(time.UnixMilli(*state.LastRefresh)) > 30*time.Minute
		if due {
			go func() {
				a.svc.Refresh(a.ctx)
			}()
		}
	}
	ticker := time.NewTicker(30 * time.Minute)
	go func() {
		for range ticker.C {
			if a.svc.State().Settings.AutoRefresh {
				a.svc.Refresh(a.ctx)
			}
		}
	}()
}

func (a *App) beforeClose(ctx context.Context) bool {
	a.finishRead()
	if a.host != nil {
		a.host.Close()
	}
	a.chatMu.Lock()
	chat := a.chat
	a.chatMu.Unlock()
	if chat != nil {
		chat.Close()
	}
	a.extractorMu.Lock()
	extractor := a.extractor
	a.extractorMu.Unlock()
	if extractor != nil {
		extractor.Close()
	}
	a.translatorMu.Lock()
	translator := a.translator
	translatorWindow := a.translatorWindow
	a.translatorMu.Unlock()
	if translator != nil {
		translator.Close()
	}
	if translatorWindow != nil {
		translatorWindow.Close()
	}
	a.chatMu.Lock()
	chatPopup := a.chatPopup
	a.chatMu.Unlock()
	if chatPopup != nil {
		chatPopup.Close()
	}
	a.deepseekMu.Lock()
	deepseek := a.deepseek
	deepseekPopup := a.deepseekPopup
	a.deepseekMu.Unlock()
	if deepseek != nil {
		deepseek.Close()
	}
	if deepseekPopup != nil {
		deepseekPopup.Close()
	}
	if a.tempWebRoot != "" {
		_ = os.RemoveAll(a.tempWebRoot)
	}
	return false
}

func (a *App) State() any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.State()
}

func (a *App) Settings(values map[string]any) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.Settings(values)
}

func (a *App) Refresh() any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.Refresh(a.ctx)
}

func (a *App) ReaderBounds(bounds map[string]float64) bool {
	if a.host == nil {
		return false
	}
	width, height := bounds["width"], bounds["height"]
	if width <= 0 || height <= 0 {
		return false
	}
	a.host.SetBoundsCSS(bounds["x"], bounds["y"], width, height)
	return true
}

func (a *App) ReaderOpen(article map[string]any) map[string]any {
	if a.host == nil {
		return map[string]any{"error": "阅读视图尚未就绪"}
	}
	url, _ := article["url"].(string)
	safe, ok := rank.SafeURL(url)
	if !ok {
		return map[string]any{"error": "请输入有效的 HTTP(S) 网址"}
	}
	a.access.Reset()
	a.setReaderURL(safe)
	a.finishRead()
	a.hideChat()
	a.hideDeepSeek()
	a.host.Show(true)
	a.host.Navigate(safe)
	if a.svc != nil && a.svc.RecordHistoryEnabled() {
		a.svc.AddHistory(articleFromUI(article))
		a.readMu.Lock()
		a.readStart = time.Now()
		a.readMu.Unlock()
	}
	return article
}

func (a *App) ReaderShow() bool {
	if a.host == nil {
		return false
	}
	a.host.Show(true)
	return true
}

func (a *App) ReaderHide() bool {
	if a.host == nil {
		return false
	}
	a.finishRead()
	a.hideChat()
	a.hideDeepSeek()
	a.host.Show(false)
	return true
}

func (a *App) ReaderControl(action string) any {
	if action == "external" {
		url := a.currentReaderURL()
		if url == "" {
			return map[string]any{"error": "没有可打开的原文"}
		}
		wailsRuntime.BrowserOpenURL(a.ctx, url)
		return nil
	}
	if a.host != nil {
		a.host.Control(action)
	}
	return nil
}

func (a *App) ReaderExtract() any {
	if a.host == nil {
		return map[string]any{"error": "阅读视图尚未就绪"}
	}
	raw, err := a.host.Extract(25 * time.Second)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	a.host.Show(false)
	return a.decodeExtraction(raw)
}

// ExtractArticle 在隐藏视图里加载文章并抽取正文：阅读模式不必先打开原站页面。
func (a *App) ExtractArticle(article map[string]any) any {
	url, _ := article["url"].(string)
	safe, ok := rank.SafeURL(url)
	if !ok {
		return map[string]any{"error": "请输入有效的 HTTP(S) 网址"}
	}
	host, err := a.ensureExtractor()
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	a.access.Reset()
	a.setReaderURL(safe)
	a.finishRead()
	a.hideChat()
	a.hideDeepSeek()
	if a.host != nil {
		a.host.Show(false)
	}
	host.Navigate(safe)
	if a.svc != nil && a.svc.RecordHistoryEnabled() {
		a.svc.AddHistory(articleFromUI(article))
		a.readMu.Lock()
		a.readStart = time.Now()
		a.readMu.Unlock()
	}
	waitForPageLoad(host, 30*time.Second)
	if ad := a.svc.AdScript(); ad != "" {
		host.Eval(ad)
		time.Sleep(400 * time.Millisecond)
	}
	raw, err := host.Extract(25 * time.Second)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	return a.decodeExtraction(raw)
}

func (a *App) ensureExtractor() (*webhost.Host, error) {
	a.extractorMu.Lock()
	defer a.extractorMu.Unlock()
	if a.extractor != nil {
		return a.extractor, nil
	}
	host := webhost.New(webhost.Options{
		DataPath:      a.webDataPath("extractor"),
		ExtractScript: extractor.MustScript(),
	})
	if err := host.Start(15 * time.Second); err != nil {
		return nil, err
	}
	a.extractor = host
	return host, nil
}

// waitForPageLoad 轮询 readyState：隐藏视图没有可见性事件，只能主动等待。
func waitForPageLoad(host *webhost.Host, timeout time.Duration) {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		time.Sleep(500 * time.Millisecond)
		raw, err := host.EvalResult("return document.readyState", 3*time.Second)
		if err == nil && strings.Trim(raw, `"`) == "complete" {
			return
		}
	}
}

func (a *App) decodeExtraction(raw string) any {
	var envelope struct {
		OK    bool            `json:"ok"`
		Data  json.RawMessage `json:"data"`
		Error string          `json:"error"`
	}
	if err := json.Unmarshal([]byte(raw), &envelope); err != nil {
		return map[string]any{"error": "无法读取提取结果"}
	}
	if !envelope.OK {
		message := envelope.Error
		if message == "" {
			message = "未找到可提取正文，请继续在原站阅读。"
		}
		return map[string]any{"error": message}
	}
	var data map[string]any
	if err := json.Unmarshal(envelope.Data, &data); err != nil {
		return map[string]any{"error": "无法读取提取结果"}
	}
	article := articleFromMap(data)
	if article.URL == "" {
		article.URL = a.currentReaderURL()
	}
	if _, ok := rank.SafeURL(article.URL); !ok {
		return map[string]any{"error": "无法确认文章地址"}
	}
	a.access.Verify(article)
	data["publicVerified"] = false
	data["verificationPending"] = true
	return data
}

func (a *App) ReaderRestoreView(data map[string]any) any {
	article := articleFromMap(data)
	if _, ok := rank.SafeURL(article.URL); !ok || len(article.Paragraphs) == 0 {
		return map[string]any{"error": "无法恢复此精读视图，请重新打开原文"}
	}
	if a.host != nil {
		a.host.Show(false)
	}
	a.access.Restore(article)
	data["publicVerified"] = false
	data["verificationPending"] = true
	return data
}

func (a *App) AutoTranslationAuthorize() any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	provider := a.svc.TranslationProvider()
	result, err := a.access.Authorize(a.ctx, provider, nil)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	return result
}

func (a *App) External(url string) any {
	safe, ok := rank.SafeURL(url)
	if !ok {
		return map[string]any{"error": "请输入有效的 HTTP(S) 网址"}
	}
	wailsRuntime.BrowserOpenURL(a.ctx, safe)
	return nil
}

func (a *App) TranslationConfig(values map[string]any) (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	state, err := a.svc.TranslationConfig(values)
	if err != nil {
		return nil, err
	}
	return state, nil
}

func (a *App) Translate(payload map[string]any) (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	inline, _ := payload["inline"].(bool)
	if inline {
		if err := a.access.Validate(payload, a.svc.TranslationProvider()); err != nil {
			return nil, err
		}
	}
	result, err := a.svc.Translate(payload, a.translateInline, a.openTranslatorWeb)
	if err != nil {
		return nil, err
	}
	return result, nil
}

func (a *App) TranslationTest() (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	return a.svc.TranslationTest(a.translateInline, a.openTranslatorWeb)
}

func (a *App) Plugins() any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return map[string]any{"plugins": a.svc.PluginInfos()}
}

func (a *App) PluginToggle(id string, enabled bool) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	state := a.svc.SetPluginEnabled(id, enabled)
	if id == "lumen-adblock" && a.host != nil {
		a.host.Control("reload")
	}
	return state
}

func (a *App) PluginInstall() (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	path, err := wailsRuntime.OpenFileDialog(a.ctx, wailsRuntime.OpenDialogOptions{
		Title:   "导入 Lumen 插件",
		Filters: []wailsRuntime.FileFilter{{DisplayName: "Lumen 插件包", Pattern: "*.zip;*.js;*.lumenplugin"}},
	})
	if err != nil || path == "" {
		return nil, err
	}
	_, state, err := a.svc.InstallPlugin(path)
	if err != nil {
		return nil, err
	}
	return state, nil
}

func (a *App) PluginRemove(id string) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	state, err := a.svc.RemovePlugin(id)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	return state
}

func (a *App) PluginOpenFolder() any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	if err := exec.Command("explorer", a.svc.PluginDir()).Start(); err != nil {
		return map[string]any{"error": err.Error()}
	}
	return map[string]any{"ok": true}
}

func (a *App) DeepSeekConfig(key string) (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	state, err := a.svc.DeepSeekConfig(key)
	if err != nil {
		return nil, err
	}
	return state, nil
}

func (a *App) DeepSeekAsk(question, contextText string) (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	return a.svc.DeepSeekAsk(a.ctx, question, contextText)
}

func (a *App) Lookup(text string) any {
	text = strings.TrimSpace(text)
	if runes := []rune(text); len(runes) > 80 {
		text = string(runes[:80])
	}
	valid := regexp.MustCompile(`^[a-zA-Z][a-zA-Z' -]*$`)
	if !valid.MatchString(text) {
		return map[string]any{"error": "请选择英文单词或短语"}
	}
	request, err := http.NewRequest(http.MethodGet, "https://api.dictionaryapi.dev/api/v2/entries/en/"+url.PathEscape(strings.ToLower(text)), nil)
	if err != nil {
		return map[string]any{"error": "词典暂时不可用或未收录这个词，可使用下方在线词典。"}
	}
	client := &http.Client{Timeout: 15 * time.Second, Transport: &http.Transport{Proxy: http.ProxyFromEnvironment}}
	response, err := client.Do(request)
	if err != nil {
		return map[string]any{"error": "词典暂时不可用或未收录这个词，可使用下方在线词典。"}
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return map[string]any{"error": "词典暂时不可用或未收录这个词，可使用下方在线词典。"}
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, 1_000_000))
	if err != nil {
		return map[string]any{"error": "词典暂时不可用或未收录这个词，可使用下方在线词典。"}
	}
	var entries any
	if err := json.Unmarshal(body, &entries); err != nil {
		return map[string]any{"error": "词典暂时不可用或未收录这个词，可使用下方在线词典。"}
	}
	return map[string]any{"entries": entries}
}

func (a *App) ensureTranslator() (*webhost.Host, error) {
	a.translatorMu.Lock()
	defer a.translatorMu.Unlock()
	if a.translator != nil {
		return a.translator, nil
	}
	host := webhost.New(webhost.Options{
		DataPath: a.webDataPath("translator"),
	})
	if err := host.Start(15 * time.Second); err != nil {
		return nil, err
	}
	a.translator = host
	return host, nil
}

func (a *App) translateInline(text string) (string, error) {
	a.translateMu.Lock()
	defer a.translateMu.Unlock()
	host, err := a.ensureTranslator()
	if err != nil {
		return "", err
	}
	host.Navigate("https://www.bing.com/translator?from=en&to=zh-Hans&text=" + url.QueryEscape(text))
	deadline := time.Now().Add(35 * time.Second)
	script := `return JSON.stringify({input:(document.querySelector('#tta_input_ta')||{}).textContent||'',output:(document.querySelector('#tta_output_ta')||{}).textContent||''})`
	for time.Now().Before(deadline) {
		time.Sleep(500 * time.Millisecond)
		raw, evalErr := host.EvalResult(script, 3*time.Second)
		if evalErr != nil {
			continue
		}
		var value struct {
			Input  string `json:"input"`
			Output string `json:"output"`
		}
		if json.Unmarshal([]byte(raw), &value) != nil {
			continue
		}
		if strings.TrimSpace(value.Input) == strings.TrimSpace(text) && regexp.MustCompile(`[\p{Han}]`).MatchString(value.Output) {
			return strings.TrimSpace(value.Output), nil
		}
	}
	return "", fmt.Errorf("微软网页翻译超时，请重试或配置 Microsoft / DeepL 官方 API。")
}

func (a *App) openTranslatorWeb(text string) map[string]any {
	target := "https://www.bing.com/translator?from=en&to=zh-Hans&text=" + url.QueryEscape(text)
	a.translatorMu.Lock()
	window := a.translatorWindow
	if window == nil {
		host := webhost.New(webhost.Options{
			DataPath: a.webDataPath("translator-window"),
			TopLevel: true,
			Title:    "Microsoft Translator · Lumen",
			Width:    1100,
			Height:   780,
		})
		if err := host.Start(15 * time.Second); err == nil {
			window = host
			a.translatorWindow = host
		}
	}
	a.translatorMu.Unlock()
	if window == nil {
		wailsRuntime.BrowserOpenURL(a.ctx, target)
		return map[string]any{
			"web": true, "provider": "Microsoft Translator 官方网页", "at": time.Now().UnixMilli(),
			"text": "无法创建翻译窗口，已在系统浏览器打开微软官方翻译。",
		}
	}
	window.Navigate(target)
	window.Show(true)
	return map[string]any{
		"web": true, "provider": "Microsoft Translator 官方网页", "at": time.Now().UnixMilli(),
		"text": "已在微软官方翻译窗口打开所选内容。网页译文由微软直接显示；可将译文复制到下方再保存。",
	}
}

func (a *App) OpenEdge() any {
	url := a.currentReaderURL()
	if _, ok := rank.SafeURL(url); !ok {
		return map[string]any{"error": "没有可打开的原文"}
	}
	if err := exec.Command("rundll32", "url.dll,FileProtocolHandler", "microsoft-edge:"+url).Start(); err != nil {
		return map[string]any{"error": err.Error()}
	}
	return nil
}

func (a *App) handleReaderMessage(message string) {
	var payload struct {
		Type     string `json:"type"`
		URL      string `json:"url"`
		Title    string `json:"title"`
		Text     string `json:"text"`
		Context  string `json:"context"`
		Back     bool   `json:"back"`
		Download bool   `json:"download"`
	}
	if err := json.Unmarshal([]byte(message), &payload); err != nil {
		return
	}
	switch payload.Type {
	case "selection":
		wailsRuntime.EventsEmit(a.ctx, "selection", map[string]any{
			"text": payload.Text, "context": payload.Context, "url": payload.URL,
		})
	case "open-external":
		if payload.Download {
			return
		}
		if safe, ok := rank.SafeURL(payload.URL); ok {
			wailsRuntime.BrowserOpenURL(a.ctx, safe)
		}
	case "location":
		if a.svc != nil && a.host != nil {
			if safe, ok := rank.SafeURL(payload.URL); ok && safe != "" {
				a.host.Eval(a.svc.AdScript())
			}
		}
		if strings.HasPrefix(payload.URL, "chrome-error:") {
			wailsRuntime.EventsEmit(a.ctx, "reader-state", map[string]any{
				"url": payload.URL, "title": payload.Title, "loading": false,
				"back": false, "forward": false, "error": "原站加载失败，请检查网络或在系统浏览器打开。",
			})
			return
		}
		if _, ok := rank.SafeURL(payload.URL); ok {
			a.setReaderURL(payload.URL)
		}
		wailsRuntime.EventsEmit(a.ctx, "reader-state", map[string]any{
			"url": payload.URL, "title": payload.Title, "loading": false,
			"back": payload.Back, "forward": false,
		})
	}
}

func (a *App) setReaderURL(url string) {
	a.urlMu.Lock()
	a.readerURL = url
	a.urlMu.Unlock()
}

func (a *App) currentReaderURL() string {
	a.urlMu.Lock()
	defer a.urlMu.Unlock()
	return a.readerURL
}

func articleFromMap(data map[string]any) *access.Article {
	article := &access.Article{}
	if title, ok := data["title"].(string); ok {
		article.Title = title
	}
	if url, ok := data["url"].(string); ok {
		article.URL = url
	}
	if raw, ok := data["paragraphs"]; ok {
		encoded, _ := json.Marshal(raw)
		_ = json.Unmarshal(encoded, &article.Paragraphs)
	}
	return article
}

func articleFromUI(data map[string]any) model.Article {
	encoded, _ := json.Marshal(data)
	var article model.Article
	_ = json.Unmarshal(encoded, &article)
	return article
}

func (a *App) finishRead() {
	a.readMu.Lock()
	started := a.readStart
	a.readStart = time.Time{}
	a.readMu.Unlock()
	if started.IsZero() || a.svc == nil {
		return
	}
	seconds := int64(time.Since(started) / time.Second)
	if seconds > 0 {
		a.svc.AddReading(seconds)
	}
}

func (a *App) VocabAdd(item map[string]any) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.VocabAdd(item)
}

func (a *App) VocabDelete(id string) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.VocabDelete(id)
}

func (a *App) VocabReview(id string, known bool) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.VocabReview(id, known)
}

func (a *App) SavedToggle(article map[string]any) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.SavedToggle(articleFromUI(article))
}

func (a *App) HistoryDelete(url string) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.HistoryDelete(url)
}

func (a *App) HistoryClear() any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	confirmed, err := a.confirm("清空阅读足迹", "清空全部阅读足迹和阅读时长？\n\n生词本和稍后阅读将保留。", "清空足迹")
	if err != nil || !confirmed {
		return nil
	}
	a.readMu.Lock()
	a.readStart = time.Time{}
	a.readMu.Unlock()
	return a.svc.HistoryClear()
}

func (a *App) StorageInfo() any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.StorageInfo()
}

func (a *App) StorageSettings(values map[string]any) any {
	if a.svc == nil {
		return map[string]any{"error": "资料库尚未就绪"}
	}
	return a.svc.StorageSettings(values)
}

func (a *App) StorageClear(kind string) (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	messages := map[string]string{
		"articles":     "清理已抓取的文章列表缓存？\n\n生词、收藏、阅读足迹和 API 密钥均保留。清理后可重新更新获取。",
		"translations": "清理全部译文缓存？\n\n生词、收藏、阅读足迹和 API 密钥均保留。",
		"web":          "清理网页缓存？\n\n生词、收藏、阅读足迹和 API 密钥均保留。网站登录状态会保留。",
		"all":          "一键清理全部缓存？\n\n生词、收藏、阅读足迹和 API 密钥均保留。网站登录状态会保留。",
		"sites":        "清除所有原站与翻译网页的本地网站数据？\n\n将清除 Cookie、网站存储与网页缓存，并退出网站登录。生词、收藏、阅读足迹、API 密钥均保留。",
	}
	message, ok := messages[kind]
	if !ok {
		return nil, fmt.Errorf("未知清理类型")
	}
	confirmed, err := a.confirm("清理缓存", message, "确认清理")
	if err != nil || !confirmed {
		return nil, nil
	}
	return a.svc.StorageClear(kind)
}

func (a *App) Export(kind string) (bool, error) {
	if a.svc == nil {
		return false, fmt.Errorf("资料库尚未就绪")
	}
	isCSV := kind == "vocabulary"
	filename := "Lumen-学习备份.json"
	displayName := "Lumen 学习备份"
	pattern := "*.json"
	if isCSV {
		filename = "Lumen-生词本.csv"
		displayName = "CSV"
		pattern = "*.csv"
	}
	path, err := wailsRuntime.SaveFileDialog(a.ctx, wailsRuntime.SaveDialogOptions{
		DefaultFilename: filename,
		Title:           "导出 " + filename,
		Filters:         []wailsRuntime.FileFilter{{DisplayName: displayName, Pattern: pattern}},
	})
	if err != nil || path == "" {
		return false, err
	}
	var data []byte
	if isCSV {
		data = a.svc.ExportVocabularyCSV()
	} else {
		data, err = a.svc.ExportBackup()
		if err != nil {
			return false, err
		}
	}
	if err := os.WriteFile(path, data, 0o644); err != nil {
		return false, err
	}
	return true, nil
}

func (a *App) Import() (any, error) {
	if a.svc == nil {
		return nil, fmt.Errorf("资料库尚未就绪")
	}
	path, err := wailsRuntime.OpenFileDialog(a.ctx, wailsRuntime.OpenDialogOptions{
		Title:   "导入 Lumen 学习备份",
		Filters: []wailsRuntime.FileFilter{{DisplayName: "Lumen 学习备份", Pattern: "*.json"}},
	})
	if err != nil || path == "" {
		return nil, err
	}
	info, err := os.Stat(path)
	if err != nil {
		return nil, err
	}
	if info.Size() > 20_000_000 {
		return nil, fmt.Errorf("备份文件过大")
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return a.svc.ImportBackup(data)
}

func (a *App) confirm(title, message, confirmButton string) (bool, error) {
	choice, err := wailsRuntime.MessageDialog(a.ctx, wailsRuntime.MessageDialogOptions{
		Type:          wailsRuntime.QuestionDialog,
		Title:         title,
		Message:       message,
		Buttons:       []string{"取消", confirmButton},
		DefaultButton: "取消",
		CancelButton:  "取消",
	})
	if err != nil {
		return false, err
	}
	return choice == confirmButton, nil
}

func (a *App) ensureChat() (*webhost.Host, error) {
	a.chatMu.Lock()
	defer a.chatMu.Unlock()
	if a.chat != nil {
		return a.chat, nil
	}
	host := webhost.New(webhost.Options{
		DataPath:  a.webDataPath("chat"),
		OnMessage: a.handleChatMessage,
	})
	if err := host.Start(15 * time.Second); err != nil {
		return nil, err
	}
	host.Navigate("https://chatgpt.com/")
	a.chat = host
	return host, nil
}

func (a *App) ensureDeepSeek() (*webhost.Host, error) {
	a.deepseekMu.Lock()
	defer a.deepseekMu.Unlock()
	if a.deepseek != nil {
		return a.deepseek, nil
	}
	host := webhost.New(webhost.Options{
		DataPath:  a.webDataPath("deepseek"),
		OnMessage: a.handleDeepSeekMessage,
	})
	if err := host.Start(15 * time.Second); err != nil {
		return nil, err
	}
	host.Navigate("https://chat.deepseek.com/")
	a.deepseek = host
	return host, nil
}

func (a *App) DeepSeekShow() bool {
	host, err := a.ensureDeepSeek()
	if err != nil {
		wailsRuntime.EventsEmit(a.ctx, "deepseek-status", map[string]any{"loading": false, "error": err.Error()})
		return false
	}
	a.deepseekMu.Lock()
	bounds := a.deepseekBounds
	a.deepseekMu.Unlock()
	if bounds != nil {
		host.SetBoundsCSS(bounds["x"], bounds["y"], bounds["width"], bounds["height"])
	}
	host.Show(true)
	wailsRuntime.EventsEmit(a.ctx, "deepseek-status", map[string]any{"loading": true})
	return true
}

func (a *App) DeepSeekHide() bool {
	a.hideDeepSeek()
	return true
}

func (a *App) DeepSeekBounds(bounds map[string]float64) bool {
	if bounds["width"] <= 0 || bounds["height"] <= 0 {
		return false
	}
	a.deepseekMu.Lock()
	a.deepseekBounds = map[string]float64{
		"x": bounds["x"], "y": bounds["y"], "width": bounds["width"], "height": bounds["height"],
	}
	host := a.deepseek
	a.deepseekMu.Unlock()
	if host != nil {
		host.SetBoundsCSS(bounds["x"], bounds["y"], bounds["width"], bounds["height"])
	}
	return true
}

func (a *App) DeepSeekReload() bool {
	a.deepseekMu.Lock()
	host := a.deepseek
	a.deepseekMu.Unlock()
	if host != nil {
		host.Control("reload")
	}
	return true
}

func (a *App) DeepSeekPopout() bool {
	a.deepseekMu.Lock()
	if a.deepseekPopup == nil {
		host := webhost.New(webhost.Options{
			DataPath:  a.webDataPath("deepseek-popup"),
			OnMessage: a.handleDeepSeekMessage,
			TopLevel:  true,
			Title:     "DeepSeek · Lumen 阅读提问",
			Width:     500,
			Height:    760,
		})
		if err := host.Start(15 * time.Second); err == nil {
			host.Navigate("https://chat.deepseek.com/")
			a.deepseekPopup = host
		}
	}
	window := a.deepseekPopup
	a.deepseekMu.Unlock()
	if window == nil {
		wailsRuntime.BrowserOpenURL(a.ctx, "https://chat.deepseek.com/")
		return false
	}
	window.Show(true)
	return true
}

func (a *App) hideDeepSeek() {
	a.deepseekMu.Lock()
	host := a.deepseek
	a.deepseekMu.Unlock()
	if host != nil {
		host.Show(false)
	}
}

func (a *App) handleDeepSeekMessage(message string) {
	var payload struct {
		Type  string `json:"type"`
		URL   string `json:"url"`
		Title string `json:"title"`
	}
	if json.Unmarshal([]byte(message), &payload) != nil {
		return
	}
	if payload.Type == "open-external" {
		if safe, ok := rank.SafeURL(payload.URL); ok {
			wailsRuntime.BrowserOpenURL(a.ctx, safe)
		}
		return
	}
	if payload.Type != "location" {
		return
	}
	if strings.HasPrefix(payload.URL, "chrome-error:") {
		wailsRuntime.EventsEmit(a.ctx, "deepseek-status", map[string]any{
			"loading": false, "error": "无法连接 chat.deepseek.com，请检查网络或使用浏览器打开。",
		})
		return
	}
	wailsRuntime.EventsEmit(a.ctx, "deepseek-status", map[string]any{
		"loading": false, "url": payload.URL, "title": payload.Title,
	})
}

func (a *App) ChatShow() bool {
	host, err := a.ensureChat()
	if err != nil {
		wailsRuntime.EventsEmit(a.ctx, "chat-status", map[string]any{"loading": false, "error": err.Error()})
		return false
	}
	a.chatMu.Lock()
	bounds := a.chatBounds
	a.chatMu.Unlock()
	if bounds != nil {
		host.SetBoundsCSS(bounds["x"], bounds["y"], bounds["width"], bounds["height"])
	}
	host.Show(true)
	wailsRuntime.EventsEmit(a.ctx, "chat-status", map[string]any{"loading": true})
	return true
}

func (a *App) ChatHide() bool {
	a.hideChat()
	return true
}

func (a *App) ChatBounds(bounds map[string]float64) bool {
	if bounds["width"] <= 0 || bounds["height"] <= 0 {
		return false
	}
	a.chatMu.Lock()
	a.chatBounds = map[string]float64{
		"x": bounds["x"], "y": bounds["y"], "width": bounds["width"], "height": bounds["height"],
	}
	host := a.chat
	a.chatMu.Unlock()
	if host != nil {
		host.SetBoundsCSS(bounds["x"], bounds["y"], bounds["width"], bounds["height"])
	}
	return true
}

func (a *App) ChatReload() bool {
	a.chatMu.Lock()
	host := a.chat
	a.chatMu.Unlock()
	if host != nil {
		host.Control("reload")
	}
	return true
}

func (a *App) ChatPopout() bool {
	a.chatMu.Lock()
	if a.chatPopup == nil {
		host := webhost.New(webhost.Options{
			DataPath:  a.webDataPath("chat-popup"),
			OnMessage: a.handleChatMessage,
			TopLevel:  true,
			Title:     "ChatGPT · Lumen 阅读提问",
			Width:     500,
			Height:    760,
		})
		if err := host.Start(15 * time.Second); err == nil {
			host.Navigate("https://chatgpt.com/")
			a.chatPopup = host
		}
	}
	window := a.chatPopup
	a.chatMu.Unlock()
	if window == nil {
		wailsRuntime.BrowserOpenURL(a.ctx, "https://chatgpt.com/")
		return false
	}
	window.Show(true)
	return true
}

func (a *App) ChatCopyContext(text string) bool {
	if runes := []rune(text); len(runes) > 15000 {
		text = string(runes[:15000])
	}
	wailsRuntime.ClipboardSetText(a.ctx, text)
	return true
}

func (a *App) hideChat() {
	a.chatMu.Lock()
	host := a.chat
	a.chatMu.Unlock()
	if host != nil {
		host.Show(false)
	}
}

func (a *App) handleChatMessage(message string) {
	var payload struct {
		Type  string `json:"type"`
		URL   string `json:"url"`
		Title string `json:"title"`
	}
	if json.Unmarshal([]byte(message), &payload) != nil {
		return
	}
	if payload.Type == "open-external" {
		if safe, ok := rank.SafeURL(payload.URL); ok {
			wailsRuntime.BrowserOpenURL(a.ctx, safe)
		}
		return
	}
	if payload.Type != "location" {
		return
	}
	if strings.HasPrefix(payload.URL, "chrome-error:") {
		wailsRuntime.EventsEmit(a.ctx, "chat-status", map[string]any{
			"loading": false, "error": "无法连接 chatgpt.com，请检查网络、代理或使用浏览器打开。",
		})
		return
	}
	wailsRuntime.EventsEmit(a.ctx, "chat-status", map[string]any{
		"loading": false, "url": payload.URL, "title": payload.Title,
	})
}
