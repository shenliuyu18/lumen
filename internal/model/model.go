package model

type Weights struct {
	Freshness int `json:"freshness"`
	Reach     int `json:"reach"`
	Buzz      int `json:"buzz"`
	Frontier  int `json:"frontier"`
	Learning  int `json:"learning"`
}

var DefaultWeights = Weights{Freshness: 30, Reach: 20, Buzz: 15, Frontier: 20, Learning: 15}

type TranslationSettings struct {
	Provider   string `json:"provider"`
	Region     string `json:"region"`
	DeeplPlan  string `json:"deeplPlan"`
	UseContext bool   `json:"useContext"`
}

type Settings struct {
	Theme           string              `json:"theme"`
	Weights         Weights             `json:"weights"`
	Interests       []string            `json:"interests"`
	DisabledSources []string            `json:"disabledSources"`
	FontSize        int                 `json:"fontSize"`
	AutoRefresh     bool                `json:"autoRefresh"`
	RecordHistory   bool                `json:"recordHistory"`
	LowStorage      bool                `json:"lowStorage"`
	DisabledPlugins []string            `json:"disabledPlugins"`
	Translation     TranslationSettings `json:"translation"`
}

func DefaultSettings() Settings {
	return Settings{
		Theme:           "sage",
		Weights:         DefaultWeights,
		Interests:       []string{},
		DisabledSources: []string{},
		FontSize:        18,
		AutoRefresh:     true,
		RecordHistory:   true,
		Translation:     TranslationSettings{Provider: "microsoft-web", DeeplPlan: "free", UseContext: true},
	}
}

type Source struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Category    string `json:"category"`
	Region      string `json:"region"`
	URL         string `json:"url"`
	Feed        string `json:"feed"`
	Reach       int    `json:"reach"`
	Description string `json:"description"`
}

type Quality struct {
	Score   int      `json:"score"`
	Reasons []string `json:"reasons"`
	Label   string   `json:"label"`
	Basis   string   `json:"basis"`
}

type Metrics struct {
	Freshness int `json:"freshness"`
	Reach     int `json:"reach"`
	Buzz      int `json:"buzz"`
	Frontier  int `json:"frontier"`
	Learning  int `json:"learning"`
}

type Article struct {
	ID          string   `json:"id"`
	Title       string   `json:"title"`
	URL         string   `json:"url"`
	SourceID    string   `json:"sourceId"`
	Source      string   `json:"source"`
	Category    string   `json:"category"`
	Region      string   `json:"region"`
	Reach       int      `json:"reach"`
	Description string   `json:"description"`
	PublishedAt *int64   `json:"publishedAt"`
	FetchedAt   int64    `json:"fetchedAt"`
	Score       int      `json:"score,omitempty"`
	Quality     *Quality `json:"quality,omitempty"`
	Metrics     *Metrics `json:"metrics,omitempty"`
	PeerCount   int      `json:"peerCount,omitempty"`
	Interest    int      `json:"interest,omitempty"`
}

func (a Article) Published() int64 {
	if a.PublishedAt == nil {
		return 0
	}
	return *a.PublishedAt
}

type SavedArticle struct {
	Article
	SavedAt int64 `json:"savedAt"`
}

type HistoryEntry struct {
	Article
	ReadAt int64 `json:"readAt"`
}

type Vocab struct {
	ID         string `json:"id"`
	Text       string `json:"text"`
	Meaning    string `json:"meaning"`
	Context    string `json:"context"`
	URL        string `json:"url"`
	CreatedAt  int64  `json:"createdAt"`
	DueAt      int64  `json:"dueAt"`
	Level      int    `json:"level"`
	ReviewedAt int64  `json:"reviewedAt,omitempty"`
}

type Translation struct {
	Text     string `json:"text"`
	Provider string `json:"provider"`
	At       int64  `json:"at"`
	Cached   bool   `json:"cached,omitempty"`
	Web      bool   `json:"web,omitempty"`
}

type Status struct {
	OK    bool   `json:"ok"`
	Count int    `json:"count,omitempty"`
	Error string `json:"error,omitempty"`
	At    int64  `json:"at"`
}

type NewsroomGroup struct {
	SourceID  string    `json:"sourceId"`
	Basis     *string   `json:"basis"`
	Items     []Article `json:"items"`
	CheckedAt int64     `json:"checkedAt"`
	FetchedAt *int64    `json:"fetchedAt"`
	Stale     bool      `json:"stale"`
	Error     *string   `json:"error"`
}

type Newsroom struct {
	UpdatedAt    int64           `json:"updatedAt"`
	Groups       []NewsroomGroup `json:"groups"`
	SourceCount  int             `json:"sourceCount"`
	CoveredCount int             `json:"coveredCount"`
	StoryCount   int             `json:"storyCount"`
}

type Store struct {
	Version         int                    `json:"version"`
	Settings        Settings               `json:"settings"`
	Articles        []Article              `json:"articles"`
	Statuses        map[string]Status      `json:"statuses"`
	Vocabulary      []Vocab                `json:"vocabulary"`
	Saved           []SavedArticle         `json:"saved"`
	History         []HistoryEntry         `json:"history"`
	Translations    map[string]Translation `json:"translations"`
	ReadingSeconds  int64                  `json:"readingSeconds"`
	LastRefresh     *int64                 `json:"lastRefresh,omitempty"`
	Newsroom        *Newsroom              `json:"newsroom,omitempty"`
	NewsroomCleared bool                   `json:"newsroomCleared,omitempty"`
	ArticlesCleared bool                   `json:"articlesCleared,omitempty"`
}

func DefaultStore() Store {
	return Store{
		Version:      1,
		Settings:     DefaultSettings(),
		Articles:     []Article{},
		Statuses:     map[string]Status{},
		Vocabulary:   []Vocab{},
		Saved:        []SavedArticle{},
		History:      []HistoryEntry{},
		Translations: map[string]Translation{},
	}
}

type NewsroomRule struct {
	ExcludeSelector        string   `json:"excludeSelector"`
	HeadingSelector        string   `json:"headingSelector"`
	TitleMin               int      `json:"titleMin"`
	TitleMax               int      `json:"titleMax"`
	DenyPathPattern        string   `json:"denyPathPattern"`
	DenyTitlePattern       string   `json:"denyTitlePattern"`
	DenyTitles             []string `json:"denyTitles"`
	InstitutionPathPattern string   `json:"institutionPathPattern"`
	StoryPathPattern       string   `json:"storyPathPattern"`
	MaxItems               int      `json:"maxItems"`
}

type NewsroomSpec struct {
	Default            NewsroomRule            `json:"default"`
	InstitutionRegion  string                  `json:"institutionRegion"`
	InstitutionSources []string                `json:"institutionSources"`
	Overrides          map[string]NewsroomRule `json:"overrides"`
}

type PluginInfo struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Version     string `json:"version"`
	Type        string `json:"type"`
	Description string `json:"description"`
	Author      string `json:"author"`
	License     string `json:"license"`
	Enabled     bool   `json:"enabled"`
	SourceCount int    `json:"sourceCount"`
	RuleCount   int    `json:"ruleCount"`
}

type PublicState struct {
	Version         int                    `json:"version"`
	Settings        Settings               `json:"settings"`
	Articles        []Article              `json:"articles"`
	Statuses        map[string]Status      `json:"statuses"`
	Vocabulary      []Vocab                `json:"vocabulary"`
	Saved           []SavedArticle         `json:"saved"`
	History         []HistoryEntry         `json:"history"`
	Translations    map[string]Translation `json:"translations,omitempty"`
	ReadingSeconds  int64                  `json:"readingSeconds"`
	LastRefresh     *int64                 `json:"lastRefresh,omitempty"`
	Newsroom        *Newsroom              `json:"newsroom,omitempty"`
	NewsroomCleared bool                   `json:"newsroomCleared,omitempty"`
	ArticlesCleared bool                   `json:"articlesCleared,omitempty"`
	Sources         []Source               `json:"sources"`
	Plugins         []PluginInfo           `json:"plugins"`
	KeyStatus       map[string]bool        `json:"keyStatus"`
	Refreshing      bool                   `json:"refreshing"`
	UserData        string                 `json:"userData"`
}
