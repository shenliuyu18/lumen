package service

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"

	"lumen/internal/catalog"
	"lumen/internal/feed"
	"lumen/internal/model"
	"lumen/internal/netclient"
	"lumen/internal/newsroom"
	"lumen/internal/plugins"
	"lumen/internal/rank"
	"lumen/internal/secrets"
	"lumen/internal/store"
	"lumen/internal/translate"
)

type EmitFunc func(event string, data any)

type Service struct {
	mu         sync.RWMutex
	repo       *store.Repo
	store      model.Store
	sources    []model.Source
	sourceByID map[string]model.Source
	blockHosts map[string]bool
	categories map[string]bool
	fetcher    *feed.Fetcher
	secrets    *secrets.Manager
	plugins    *plugins.Manager
	refreshing bool
	emit       EmitFunc
	userData   string
}

func New() (*Service, error) {
	dir := store.UserDataDir()
	trimWebViewCaches(dir)
	applyPendingWebClear(dir)
	repo := store.New(dir)
	loaded, loadErr := repo.Load()
	if loadErr != nil {
		fmt.Println("[lumen] store:", loadErr)
	}
	pluginManager, err := plugins.New(dir)
	if err != nil {
		return nil, err
	}
	sources := pluginManager.Sources(loaded.Settings.DisabledPlugins)
	newLibraryMissing := false
	if _, err := os.Stat(repo.Path); os.IsNotExist(err) {
		newLibraryMissing = true
	}

	seeded := false
	if len(loaded.Articles) == 0 && !loaded.ArticlesCleared && !loaded.Settings.LowStorage {
		if seed, err := catalog.Snapshot(); err == nil {
			loaded.Articles = seed.Articles
			loaded.Statuses = seed.Statuses
			seeded = true
		}
	}
	if loaded.Newsroom == nil && !loaded.NewsroomCleared && !loaded.Settings.LowStorage {
		if snapshot, err := catalog.NewsroomSnapshot(); err == nil {
			loaded.Newsroom = snapshot
			seeded = true
		}
	}

	service := &Service{
		repo:       repo,
		store:      loaded,
		sources:    sources,
		sourceByID: map[string]model.Source{},
		blockHosts: map[string]bool{},
		categories: map[string]bool{},
		fetcher:    feed.NewFetcher(),
		secrets:    secrets.New(filepath.Join(dir, "translation-keys.bin")),
		plugins:    pluginManager,
		userData:   dir,
	}
	service.rebuildSources()
	if err := netclient.Configure(loaded.Settings.ProxyMode, loaded.Settings.ProxyURL); err != nil {
		fmt.Println("[lumen] proxy:", err)
	}
	if seeded {
		if err := repo.Save(loaded); err != nil {
			fmt.Println("[lumen] seed save:", err)
		}
	} else if newLibraryMissing {
		if err := repo.Save(loaded); err != nil {
			fmt.Println("[lumen] migration save:", err)
		}
	}
	return service, nil
}

func (s *Service) SetEmitter(emit EmitFunc) {
	s.emit = emit
}

func (s *Service) Sources() []model.Source {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return append([]model.Source{}, s.sources...)
}

func (s *Service) rebuildSources() {
	s.blockHosts = s.plugins.BlockHosts(s.store.Settings.DisabledPlugins)
	sources := s.plugins.Sources(s.store.Settings.DisabledPlugins)
	s.sourceByID = map[string]model.Source{}
	s.categories = map[string]bool{}
	for _, source := range sources {
		s.sourceByID[source.ID] = source
		s.categories[source.Category] = true
	}
	s.sources = sources
}

func (s *Service) PluginInfos() []model.PluginInfo {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.plugins.List(s.store.Settings.DisabledPlugins)
}

func (s *Service) SetPluginEnabled(id string, enabled bool) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	disabled := make([]string, 0, len(s.store.Settings.DisabledPlugins)+1)
	for _, value := range s.store.Settings.DisabledPlugins {
		if value != id {
			disabled = append(disabled, value)
		}
	}
	if !enabled {
		disabled = append(disabled, id)
	}
	s.store.Settings.DisabledPlugins = disabled
	s.rebuildSources()
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) InstallPlugin(path string) (model.PluginInfo, model.PublicState, error) {
	info, err := s.plugins.Install(path)
	if err != nil {
		return model.PluginInfo{}, model.PublicState{}, err
	}
	s.mu.Lock()
	s.rebuildSources()
	state := s.stateLocked()
	s.mu.Unlock()
	return info, state, nil
}

func (s *Service) RemovePlugin(id string) (model.PublicState, error) {
	if err := s.plugins.Remove(id); err != nil {
		return model.PublicState{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.rebuildSources()
	return s.stateLocked(), nil
}

func (s *Service) ReloadPlugins() model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	_ = s.plugins.Reload()
	s.rebuildSources()
	return s.stateLocked()
}

func (s *Service) PluginDir() string {
	return s.plugins.Dir()
}

// BlockURL 判断请求是否命中插件的域名屏蔽表；命中后 WebView 直接拿到空响应。
func (s *Service) BlockURL(raw string) bool {
	parsed, err := url.Parse(raw)
	if err != nil {
		return false
	}
	host := strings.ToLower(parsed.Hostname())
	if host == "" {
		return false
	}
	s.mu.Lock()
	hosts := s.blockHosts
	s.mu.Unlock()
	for {
		if hosts[host] {
			return true
		}
		dot := strings.IndexByte(host, '.')
		if dot < 0 {
			return false
		}
		host = host[dot+1:]
	}
}

func (s *Service) AdScript() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.plugins.AdScript(s.store.Settings.DisabledPlugins)
}

func (s *Service) UserData() string {
	return s.userData
}

func (s *Service) TranslationProvider() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.store.Settings.Translation.Provider
}

func (s *Service) State() model.PublicState {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.stateLocked()
}

func (s *Service) stateLocked() model.PublicState {
	articles := rank.Rank(s.store.Articles, s.store.Settings.Weights, time.Now().UnixMilli(), s.store.Settings.Interests)
	settings := s.store.Settings
	settings.Interests = append([]string{}, settings.Interests...)
	settings.DisabledSources = append([]string{}, settings.DisabledSources...)
	statuses := make(map[string]model.Status, len(s.store.Statuses))
	for id, status := range s.store.Statuses {
		statuses[id] = status
	}
	return model.PublicState{
		Version:         s.store.Version,
		Settings:        settings,
		Articles:        articles,
		Statuses:        statuses,
		Vocabulary:      append([]model.Vocab{}, s.store.Vocabulary...),
		Saved:           append([]model.SavedArticle{}, s.store.Saved...),
		History:         append([]model.HistoryEntry{}, s.store.History...),
		Translations:    nil,
		ReadingSeconds:  s.store.ReadingSeconds,
		LastRefresh:     s.store.LastRefresh,
		Newsroom:        s.store.Newsroom,
		NewsroomCleared: s.store.NewsroomCleared,
		ArticlesCleared: s.store.ArticlesCleared,
		Sources:         s.sources,
		Plugins:         s.plugins.List(s.store.Settings.DisabledPlugins),
		KeyStatus:       s.secrets.Status(),
		Refreshing:      s.refreshing,
		UserData:        s.userData,
	}
}

func (s *Service) saveLocked() {
	if err := s.repo.Save(s.store); err != nil {
		fmt.Println("[lumen] save:", err)
	}
}

func (s *Service) Settings(values map[string]any) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	settings := &s.store.Settings
	if theme, ok := values["theme"].(string); ok {
		allowed := map[string]bool{"sage": true, "sky": true, "rose": true, "sand": true, "lavender": true, "mint": true, "apricot": true, "ice": true, "pearl": true, "peach": true}
		if allowed[theme] {
			settings.Theme = theme
		}
	}
	if raw, ok := values["weights"].(map[string]any); ok {
		updateWeight := func(key string, target *int) {
			if value, ok := raw[key].(float64); ok {
				*target = clamp(int(value), 0, 100)
			}
		}
		updateWeight("freshness", &settings.Weights.Freshness)
		updateWeight("reach", &settings.Weights.Reach)
		updateWeight("buzz", &settings.Weights.Buzz)
		updateWeight("frontier", &settings.Weights.Frontier)
		updateWeight("learning", &settings.Weights.Learning)
	}
	if raw, ok := values["interests"].([]any); ok {
		interests := make([]string, 0, len(raw))
		for _, value := range raw {
			category, ok := value.(string)
			if ok && s.categories[category] {
				interests = append(interests, category)
			}
		}
		settings.Interests = interests
	}
	if raw, ok := values["disabledSources"].([]any); ok {
		disabled := make([]string, 0, len(raw))
		for _, value := range raw {
			id, ok := value.(string)
			if ok {
				if _, exists := s.sourceByID[id]; exists {
					disabled = append(disabled, id)
				}
			}
		}
		settings.DisabledSources = disabled
	}
	if value, ok := values["autoRefresh"].(bool); ok {
		settings.AutoRefresh = value
	}
	if value, ok := values["recordHistory"].(bool); ok {
		settings.RecordHistory = value
	}
	if value, ok := values["lowStorage"].(bool); ok {
		settings.LowStorage = value
	}
	if value, ok := values["glass"].(bool); ok {
		settings.Glass = value
	}
	if raw, ok := values["disabledPlugins"].([]any); ok {
		disabled := make([]string, 0, len(raw))
		for _, value := range raw {
			if id, ok := value.(string); ok {
				disabled = append(disabled, id)
			}
		}
		settings.DisabledPlugins = disabled
	}
	proxyChanged := false
	if mode, ok := values["proxyMode"].(string); ok {
		switch mode {
		case "system", "direct", "manual":
			settings.ProxyMode = mode
			proxyChanged = true
		}
	}
	if raw, ok := values["proxyUrl"].(string); ok {
		settings.ProxyURL = strings.TrimSpace(raw)
		proxyChanged = true
	}
	if proxyChanged {
		if err := netclient.Configure(settings.ProxyMode, settings.ProxyURL); err != nil {
			settings.ProxyMode = "system"
			settings.ProxyURL = ""
			_ = netclient.Configure("system", "")
		}
	}
	s.rebuildSources()
	if value, ok := values["fontSize"].(float64); ok {
		settings.FontSize = clamp(int(value), 14, 28)
	}
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) Refresh(ctx context.Context) model.PublicState {
	s.mu.Lock()
	if s.refreshing {
		state := s.stateLocked()
		s.mu.Unlock()
		return state
	}
	s.refreshing = true
	previousNewsroom := map[string]model.NewsroomGroup{}
	if s.store.Newsroom != nil {
		for _, group := range s.store.Newsroom.Groups {
			previousNewsroom[group.SourceID] = group
		}
	}
	disabled := map[string]bool{}
	for _, id := range s.store.Settings.DisabledSources {
		disabled[id] = true
	}
	s.mu.Unlock()

	original := make([]model.Source, 0, len(s.sources))
	desk := make([]model.Source, 0, len(s.sources))
	for _, source := range s.sources {
		if disabled[source.ID] {
			continue
		}
		desk = append(desk, source)
		if source.Feed != "" {
			original = append(original, source)
		}
	}
	total := len(original) + len(desk)
	done := 0
	var progressMu sync.Mutex
	lastBroadcast := time.Time{}
	report := func(name string) {
		progressMu.Lock()
		done++
		current := done
		if time.Since(lastBroadcast) > 2500*time.Millisecond {
			lastBroadcast = time.Now()
			progressMu.Unlock()
			s.emitEvent("feed-progress", map[string]any{"running": true, "done": current, "total": total, "source": name})
			s.emitEvent("library-updated", s.State())
			return
		}
		progressMu.Unlock()
		s.emitEvent("feed-progress", map[string]any{"running": true, "done": current, "total": total, "source": name})
	}

	feedItems := map[string][]model.Article{}
	var feedMu sync.Mutex
	statuses := map[string]model.Status{}
	var statusMu sync.Mutex
	queue := make(chan model.Source)
	var workers sync.WaitGroup
	for i := 0; i < 12; i++ {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for source := range queue {
				fetchedAt := time.Now().UnixMilli()
				data, err := s.fetcher.Fetch(ctx, source.Feed, 18*time.Second)
				if err != nil {
					statusMu.Lock()
					statuses[source.ID] = model.Status{OK: false, Error: feed.ErrorMessage(err), At: fetchedAt}
					statusMu.Unlock()
					report(source.Name)
					continue
				}
				items, err := feed.Parse(data, source, fetchedAt, 25)
				if err != nil || len(items) == 0 {
					message := "未发现有效文章"
					if err != nil {
						message = err.Error()
					}
					statusMu.Lock()
					statuses[source.ID] = model.Status{OK: false, Error: message, At: fetchedAt}
					statusMu.Unlock()
					report(source.Name)
					continue
				}
				feedMu.Lock()
				feedItems[source.ID] = items
				feedMu.Unlock()
				statusMu.Lock()
				statuses[source.ID] = model.Status{OK: true, Count: len(items), At: fetchedAt}
				statusMu.Unlock()
				s.mu.Lock()
				s.store.Articles = rank.Merge(s.store.Articles, items)
				s.store.ArticlesCleared = false
				s.mu.Unlock()
				report(source.Name)
			}
		}()
	}
	go func() {
		for _, source := range original {
			queue <- source
		}
		close(queue)
	}()

	var newsroomWait sync.WaitGroup
	var newsroomResult model.Newsroom
	newsroomWait.Add(1)
	go func() {
		defer newsroomWait.Done()
		newsroomResult = newsroom.Update(ctx, newsroom.Options{
			Sources: desk,
			Spec:    s.plugins.NewsroomSpec(s.store.Settings.DisabledPlugins),
			Fetch:   s.fetcher.Fetch,
			FeedItems: func(sourceID string) []model.Article {
				feedMu.Lock()
				defer feedMu.Unlock()
				return append([]model.Article(nil), feedItems[sourceID]...)
			},
			Previous: previousNewsroom,
			OnProgress: func(_, _ int, name string) {
				report(name)
			},
		})
	}()
	workers.Wait()
	newsroomWait.Wait()

	s.mu.Lock()
	for id, status := range statuses {
		s.store.Statuses[id] = status
	}
	s.store.Newsroom = &newsroomResult
	s.store.NewsroomCleared = false
	now := time.Now().UnixMilli()
	s.store.LastRefresh = &now
	s.refreshing = false
	s.saveLocked()
	state := s.stateLocked()
	s.mu.Unlock()

	s.emitEvent("feed-progress", map[string]any{"running": false, "done": total, "total": total})
	s.emitEvent("library-updated", state)
	return state
}

func (s *Service) emitEvent(event string, data any) {
	if s.emit != nil {
		s.emit(event, data)
	}
}

func clamp(value, low, high int) int {
	if value < low {
		return low
	}
	if value > high {
		return high
	}
	return value
}

var regionPattern = regexp.MustCompile(`[^a-z0-9-]`)

func (s *Service) TranslationConfig(values map[string]any) (model.PublicState, error) {
	s.mu.Lock()
	next := s.store.Settings.Translation
	if provider, ok := values["provider"].(string); ok {
		for _, allowed := range translate.Providers {
			if provider == allowed {
				next.Provider = provider
				break
			}
		}
	}
	if region, ok := values["region"].(string); ok {
		next.Region = regionPattern.ReplaceAllString(strings.ToLower(region), "")
		if len(next.Region) > 60 {
			next.Region = next.Region[:60]
		}
	}
	if plan, ok := values["deeplPlan"].(string); ok && (plan == "free" || plan == "pro") {
		next.DeeplPlan = plan
	}
	if useContext, ok := values["useContext"].(bool); ok {
		next.UseContext = useContext
	}
	if base, ok := values["aiBaseUrl"].(string); ok {
		base = strings.TrimSpace(base)
		if base == "" {
			base = "https://api.openai.com/v1"
		}
		if !strings.HasPrefix(base, "https://") && !strings.HasPrefix(base, "http://") {
			base = "https://" + base
		}
		if len(base) > 300 {
			base = base[:300]
		}
		next.AIBaseURL = base
	}
	if modelName, ok := values["aiModel"].(string); ok {
		modelName = strings.TrimSpace(modelName)
		if len(modelName) > 120 {
			modelName = modelName[:120]
		}
		next.AIModel = modelName
	}
	if remove, ok := values["removeKey"].(bool); ok && remove {
		if next.Provider != "microsoft" && next.Provider != "deepl" && next.Provider != "ai" {
			s.mu.Unlock()
			return model.PublicState{}, fmt.Errorf("该服务不需要密钥")
		}
		if err := s.secrets.Remove(next.Provider); err != nil {
			s.mu.Unlock()
			return model.PublicState{}, err
		}
	}
	if key, ok := values["key"].(string); ok && key != "" {
		if next.Provider != "microsoft" && next.Provider != "deepl" && next.Provider != "ai" {
			s.mu.Unlock()
			return model.PublicState{}, fmt.Errorf("该服务不需要密钥")
		}
		key = strings.TrimSpace(key)
		if len(key) > 1024 || strings.ContainsAny(key, "\r\n") {
			s.mu.Unlock()
			return model.PublicState{}, fmt.Errorf("密钥格式无效")
		}
		if err := s.secrets.Set(next.Provider, key); err != nil {
			s.mu.Unlock()
			return model.PublicState{}, err
		}
	}
	s.store.Settings.Translation = next
	s.saveLocked()
	state := s.stateLocked()
	s.mu.Unlock()
	return state, nil
}

func (s *Service) Translate(payload map[string]any, inline func(text string) (string, error), openWeb func(text string) map[string]any) (map[string]any, error) {
	text, _ := payload["text"].(string)
	text = strings.TrimSpace(text)
	contextText, _ := payload["context"].(string)
	if contextText != "" {
		if runes := []rune(contextText); len(runes) > 6000 {
			contextText = string(runes[:6000])
		}
	}
	if text == "" || len([]rune(text)) > 6000 {
		return nil, fmt.Errorf("请选择不超过 6000 字符的句子或段落。")
	}
	useInline, _ := payload["inline"].(bool)

	s.mu.RLock()
	settings := s.store.Settings.Translation
	s.mu.RUnlock()

	if settings.Provider == "microsoft-web" && !useInline {
		if openWeb == nil {
			return nil, fmt.Errorf("翻译窗口不可用")
		}
		return openWeb(text), nil
	}

	key := translate.CacheKey(settings.Provider, text, ternary(settings.UseContext, contextText, ""))
	s.mu.RLock()
	cached, hasCache := s.store.Translations[key]
	s.mu.RUnlock()
	if hasCache {
		cached.Cached = true
		return translationMap(cached), nil
	}

	result, err := s.requestTranslation(settings, text, contextText)
	if err != nil && settings.Provider == "microsoft-web" && inline != nil {
		// 免密钥网页接口被限流或改版时，回退到网页视图抓取，避免自动精读整篇中断。
		translated, webErr := inline(text)
		if webErr != nil {
			return nil, err
		}
		result = model.Translation{Text: translated, Provider: "Microsoft Translator 官方网页", At: time.Now().UnixMilli()}
	} else if err != nil {
		return nil, err
	}

	s.mu.Lock()
	s.store.Translations[key] = result
	if len(s.store.Translations) > 1500 {
		oldestKey := ""
		oldest := int64(1<<62 - 1)
		for name, value := range s.store.Translations {
			if value.At < oldest {
				oldest = value.At
				oldestKey = name
			}
		}
		if oldestKey != "" {
			delete(s.store.Translations, oldestKey)
		}
	}
	s.saveLocked()
	s.mu.Unlock()
	return translationMap(result), nil
}

func (s *Service) requestTranslation(settings model.TranslationSettings, text, contextText string) (model.Translation, error) {
	request, err := translate.BuildRequest(settings, s.secrets.Get(settings.Provider), text, ternary(settings.UseContext, contextText, ""))
	if err != nil {
		return model.Translation{}, err
	}
	httpRequest, err := translate.EncodeRequest(request)
	if err != nil {
		return model.Translation{}, err
	}
	client := netclient.New(25 * time.Second)
	response, err := client.Do(httpRequest)
	if err != nil {
		return model.Translation{}, err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		messages := map[int]string{
			401: "密钥无效或区域配置不匹配",
			403: "认证失败或无权访问，请检查 API 计划与密钥",
			429: "请求过于频繁或达到服务配额",
			456: "DeepL 字符配额已用尽",
		}
		message := messages[response.StatusCode]
		if message == "" {
			message = fmt.Sprintf("翻译服务 HTTP %d", response.StatusCode)
		}
		return model.Translation{}, fmt.Errorf("%s", message)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, 2_000_000))
	if err != nil {
		return model.Translation{}, err
	}
	return translate.ParseResponse(settings.Provider, body)
}

func (s *Service) TranslationTest(inline func(text string) (string, error), openWeb func(text string) map[string]any) (map[string]any, error) {
	return s.Translate(map[string]any{
		"text":    "The findings suggest an association, but do not establish a causal relationship.",
		"context": "A scientific study discussing the limits of observational evidence.",
	}, inline, openWeb)
}

func (s *Service) DeepSeekConfig(key string) (model.PublicState, error) {
	key = strings.TrimSpace(key)
	if key == "" {
		return model.PublicState{}, fmt.Errorf("请填写 DeepSeek API 密钥")
	}
	if len(key) > 1024 || strings.ContainsAny(key, "\r\n") {
		return model.PublicState{}, fmt.Errorf("密钥格式无效")
	}
	if err := s.secrets.Set("deepseek", key); err != nil {
		return model.PublicState{}, err
	}
	s.mu.RLock()
	state := s.stateLocked()
	s.mu.RUnlock()
	return state, nil
}

func (s *Service) DeepSeekAsk(ctx context.Context, question, contextText string) (map[string]any, error) {
	question = strings.TrimSpace(question)
	if question == "" {
		return nil, fmt.Errorf("请输入问题")
	}
	question = truncate(question, 4000)
	contextText = truncate(strings.TrimSpace(contextText), 12000)
	key := s.secrets.Get("deepseek")
	if key == "" {
		return nil, fmt.Errorf("请先在 DeepSeek 面板保存 API 密钥")
	}

	messages := []map[string]string{
		{"role": "system", "content": "你是 Lumen 的英语阅读助手。请用简体中文回答，先给结论，再解释依据。不要编造上下文没有的信息；上下文不足时请明确说明。"},
	}
	user := question
	if contextText != "" {
		user = "文章上下文：\n" + contextText + "\n\n问题：" + question
	}
	messages = append(messages, map[string]string{"role": "user", "content": user})
	payload, _ := json.Marshal(map[string]any{
		"model":       "deepseek-chat",
		"messages":    messages,
		"stream":      false,
		"temperature": 0.3,
	})
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://api.deepseek.com/chat/completions", strings.NewReader(string(payload)))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Authorization", "Bearer "+key)
	client := &http.Client{Timeout: 90 * time.Second, Transport: &http.Transport{Proxy: http.ProxyFromEnvironment}}
	response, err := client.Do(request)
	if err != nil {
		return nil, fmt.Errorf("DeepSeek 连接失败: %w", err)
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, 4_000_000))
	if err != nil {
		return nil, err
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		messagesByStatus := map[int]string{
			401: "DeepSeek 密钥无效",
			402: "DeepSeek 账户余额不足",
			429: "DeepSeek 请求过于频繁或达到配额",
		}
		message := messagesByStatus[response.StatusCode]
		if message == "" {
			message = fmt.Sprintf("DeepSeek HTTP %d", response.StatusCode)
		}
		return nil, fmt.Errorf("%s", message)
	}
	var parsed struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
		Error *struct {
			Message string `json:"message"`
		} `json:"error"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("无法解析 DeepSeek 响应")
	}
	if parsed.Error != nil && parsed.Error.Message != "" {
		return nil, fmt.Errorf("%s", parsed.Error.Message)
	}
	if len(parsed.Choices) == 0 || strings.TrimSpace(parsed.Choices[0].Message.Content) == "" {
		return nil, fmt.Errorf("DeepSeek 未返回有效回答")
	}
	return map[string]any{
		"text":  parsed.Choices[0].Message.Content,
		"model": "deepseek-chat",
		"at":    time.Now().UnixMilli(),
	}, nil
}

func translationMap(translation model.Translation) map[string]any {
	result := map[string]any{"text": translation.Text, "provider": translation.Provider, "at": translation.At}
	if translation.Cached {
		result["cached"] = true
	}
	if translation.Web {
		result["web"] = true
	}
	return result
}

func ternary(condition bool, first, second string) string {
	if condition {
		return first
	}
	return second
}

func (s *Service) RecordHistoryEnabled() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.store.Settings.RecordHistory
}

func (s *Service) VocabAdd(item map[string]any) model.PublicState {
	text := strings.TrimSpace(asString(item["text"]))
	if runes := []rune(text); len(runes) > 1500 {
		text = string(runes[:1500])
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if text == "" {
		return s.stateLocked()
	}
	for _, existing := range s.store.Vocabulary {
		if strings.EqualFold(existing.Text, text) {
			return s.stateLocked()
		}
	}
	now := time.Now().UnixMilli()
	s.store.Vocabulary = append([]model.Vocab{{
		ID:        uuid.NewString(),
		Text:      text,
		Meaning:   truncate(asString(item["meaning"]), 3000),
		Context:   truncate(asString(item["context"]), 2000),
		URL:       safeURL(asString(item["url"])),
		CreatedAt: now,
		DueAt:     now,
	}}, s.store.Vocabulary...)
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) VocabDelete(id string) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	next := make([]model.Vocab, 0, len(s.store.Vocabulary))
	for _, card := range s.store.Vocabulary {
		if card.ID != id {
			next = append(next, card)
		}
	}
	s.store.Vocabulary = next
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) VocabReview(id string, known bool) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	for i, card := range s.store.Vocabulary {
		if card.ID == id {
			s.store.Vocabulary[i] = rank.ReviewCard(card, known, time.Now().UnixMilli())
			break
		}
	}
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) SavedToggle(article model.Article) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	url := safeURL(article.URL)
	if url == "" {
		return s.stateLocked()
	}
	for i, saved := range s.store.Saved {
		if saved.URL == url {
			s.store.Saved = append(s.store.Saved[:i], s.store.Saved[i+1:]...)
			s.saveLocked()
			return s.stateLocked()
		}
	}
	article.URL = url
	s.store.Saved = append([]model.SavedArticle{{Article: article, SavedAt: time.Now().UnixMilli()}}, s.store.Saved...)
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) AddHistory(article model.Article) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.store.Settings.RecordHistory {
		return s.stateLocked()
	}
	url := safeURL(article.URL)
	if url == "" {
		return s.stateLocked()
	}
	article.URL = url
	next := []model.HistoryEntry{{Article: article, ReadAt: time.Now().UnixMilli()}}
	for _, entry := range s.store.History {
		if entry.URL != url {
			next = append(next, entry)
		}
	}
	if len(next) > 500 {
		next = next[:500]
	}
	s.store.History = next
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) HistoryDelete(url string) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	next := make([]model.HistoryEntry, 0, len(s.store.History))
	for _, entry := range s.store.History {
		if entry.URL != url {
			next = append(next, entry)
		}
	}
	s.store.History = next
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) HistoryClear() model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.store.History = []model.HistoryEntry{}
	s.store.ReadingSeconds = 0
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) AddReading(seconds int64) model.PublicState {
	if seconds <= 0 {
		return s.State()
	}
	if seconds > 1800 {
		seconds = 1800
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.store.ReadingSeconds += seconds
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) StorageSettings(values map[string]any) model.PublicState {
	s.mu.Lock()
	defer s.mu.Unlock()
	if value, ok := values["recordHistory"].(bool); ok {
		s.store.Settings.RecordHistory = value
	}
	if value, ok := values["lowStorage"].(bool); ok {
		s.store.Settings.LowStorage = value
	}
	s.saveLocked()
	return s.stateLocked()
}

func (s *Service) StorageInfo() map[string]any {
	s.mu.RLock()
	articles, _ := json.Marshal([]any{s.store.Articles, s.store.Newsroom})
	translations, _ := json.Marshal(s.store.Translations)
	lowStorage := s.store.Settings.LowStorage
	s.mu.RUnlock()
	libraryBytes := int64(0)
	if info, err := os.Stat(s.repo.Path); err == nil {
		libraryBytes = info.Size()
	}
	webviewRoot := filepath.Join(os.Getenv("APPDATA"), "Lumen", "webview2")
	var webBytes int64
	for _, name := range []string{"reader", "chat", "chat-popup", "translator", "translator-window"} {
		webBytes += directorySize(filepath.Join(webviewRoot, name))
	}
	return map[string]any{
		"webCacheBytes":     webBytes,
		"articleBytes":      len(articles),
		"translationBytes":  len(translations),
		"libraryBytes":      libraryBytes,
		"transientBrowsing": lowStorage,
	}
}

func (s *Service) StorageClear(kind string) (model.PublicState, error) {
	switch kind {
	case "articles", "translations", "web", "all", "sites":
	default:
		return model.PublicState{}, fmt.Errorf("未知清理类型")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.refreshing && (kind == "articles" || kind == "all") {
		return model.PublicState{}, fmt.Errorf("文章正在更新，请等更新结束后再清理。")
	}
	if kind == "articles" || kind == "all" {
		s.store.Articles = []model.Article{}
		s.store.Statuses = map[string]model.Status{}
		s.store.Newsroom = nil
		s.store.NewsroomCleared = true
		s.store.ArticlesCleared = true
		s.store.LastRefresh = nil
	}
	if kind == "translations" || kind == "all" {
		s.store.Translations = map[string]model.Translation{}
	}
	if kind == "web" || kind == "all" || kind == "sites" {
		marker := filepath.Join(s.userData, "webdata-clear-pending")
		_ = os.WriteFile(marker, []byte(kind), 0o644)
	}
	s.saveLocked()
	return s.stateLocked(), nil
}

func (s *Service) ExportBackup() ([]byte, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	copyStore := s.store
	copyStore.Articles = []model.Article{}
	copyStore.Statuses = map[string]model.Status{}
	copyStore.Translations = map[string]model.Translation{}
	copyStore.Newsroom = nil
	return json.MarshalIndent(map[string]any{
		"format":  "lumen-backup",
		"version": 1,
		"data":    copyStore,
	}, "", "  ")
}

func (s *Service) ExportVocabularyCSV() []byte {
	s.mu.RLock()
	vocabulary := append([]model.Vocab{}, s.store.Vocabulary...)
	s.mu.RUnlock()
	builder := strings.Builder{}
	builder.WriteString("\ufeff")
	builder.WriteString(strings.Join([]string{"英文", "释义", "原文语境", "来源", "下次复习"}, ","))
	builder.WriteString("\r\n")
	for _, card := range vocabulary {
		row := []string{
			rank.CSVCell(card.Text),
			rank.CSVCell(card.Meaning),
			rank.CSVCell(card.Context),
			rank.CSVCell(card.URL),
			rank.CSVCell(time.UnixMilli(card.DueAt).UTC().Format(time.RFC3339)),
		}
		builder.WriteString(strings.Join(row, ","))
		builder.WriteString("\r\n")
	}
	return []byte(builder.String())
}

func (s *Service) ImportBackup(data []byte) (model.PublicState, error) {
	var backup struct {
		Format  string `json:"format"`
		Version int    `json:"version"`
		Data    struct {
			Vocabulary []model.Vocab        `json:"vocabulary"`
			Saved      []model.SavedArticle `json:"saved"`
			History    []model.HistoryEntry `json:"history"`
		} `json:"data"`
	}
	if err := json.Unmarshal(data, &backup); err != nil {
		return model.PublicState{}, fmt.Errorf("不是有效的 Lumen 备份")
	}
	if backup.Format != "lumen-backup" || backup.Version != 1 ||
		backup.Data.Vocabulary == nil || backup.Data.Saved == nil || backup.Data.History == nil {
		return model.PublicState{}, fmt.Errorf("不是有效的 Lumen 备份")
	}
	for _, card := range backup.Data.Vocabulary {
		if card.ID == "" || card.Text == "" {
			return model.PublicState{}, fmt.Errorf("生词数据格式错误")
		}
	}
	for _, article := range append(append([]model.SavedArticle{}, backup.Data.Saved...), historyArticles(backup.Data.History)...) {
		if safeURL(article.URL) == "" || article.Title == "" {
			return model.PublicState{}, fmt.Errorf("阅读记录格式错误")
		}
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	vocabSeen := map[string]bool{}
	for _, card := range s.store.Vocabulary {
		vocabSeen[strings.ToLower(card.Text)] = true
	}
	for _, card := range backup.Data.Vocabulary {
		if !vocabSeen[strings.ToLower(card.Text)] {
			s.store.Vocabulary = append(s.store.Vocabulary, card)
			vocabSeen[strings.ToLower(card.Text)] = true
		}
	}
	savedSeen := map[string]bool{}
	for _, article := range s.store.Saved {
		savedSeen[article.URL] = true
	}
	for _, article := range backup.Data.Saved {
		if !savedSeen[article.URL] {
			s.store.Saved = append(s.store.Saved, article)
			savedSeen[article.URL] = true
		}
	}
	historySeen := map[string]bool{}
	for _, entry := range s.store.History {
		historySeen[entry.URL] = true
	}
	for _, entry := range backup.Data.History {
		if !historySeen[entry.URL] {
			s.store.History = append(s.store.History, entry)
			historySeen[entry.URL] = true
		}
	}
	if len(s.store.Vocabulary) > 10000 {
		s.store.Vocabulary = s.store.Vocabulary[:10000]
	}
	if len(s.store.Saved) > 10000 {
		s.store.Saved = s.store.Saved[:10000]
	}
	if len(s.store.History) > 10000 {
		s.store.History = s.store.History[:10000]
	}
	s.saveLocked()
	return s.stateLocked(), nil
}

func historyArticles(entries []model.HistoryEntry) []model.SavedArticle {
	result := make([]model.SavedArticle, 0, len(entries))
	for _, entry := range entries {
		result = append(result, model.SavedArticle{Article: entry.Article})
	}
	return result
}

func asString(value any) string {
	if value == nil {
		return ""
	}
	if text, ok := value.(string); ok {
		return text
	}
	return fmt.Sprint(value)
}

func truncate(value string, limit int) string {
	runes := []rune(value)
	if len(runes) > limit {
		return string(runes[:limit])
	}
	return value
}

func safeURL(value string) string {
	url, ok := rank.SafeURL(value)
	if !ok {
		return ""
	}
	return url
}

func directorySize(path string) int64 {
	var total int64
	_ = filepath.WalkDir(path, func(_ string, entry os.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return nil
		}
		if info, infoErr := entry.Info(); infoErr == nil {
			total += info.Size()
		}
		return nil
	})
	return total
}

func applyPendingWebClear(dir string) {
	marker := filepath.Join(dir, "webdata-clear-pending")
	data, err := os.ReadFile(marker)
	if err != nil {
		return
	}
	kind := strings.TrimSpace(string(data))
	root := filepath.Join(dir, "webview2")
	for _, role := range []string{"reader", "chat", "chat-popup", "translator", "translator-window"} {
		profile := filepath.Join(root, role)
		if kind == "sites" || kind == "sites-clear" {
			_ = os.RemoveAll(profile)
			continue
		}
		for _, cacheDir := range []string{"EBWebView/Default/Cache", "EBWebView/Default/Code Cache", "EBWebView/Default/GPUCache"} {
			_ = os.RemoveAll(filepath.Join(profile, cacheDir))
		}
	}
	_ = os.Remove(marker)
}
