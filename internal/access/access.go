package access

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

var (
	scriptPattern = regexp.MustCompile(`(?is)<script\b[^>]*>.*?</script>`)
	stylePattern  = regexp.MustCompile(`(?is)<style\b[^>]*>.*?</style>`)
	tagPattern    = regexp.MustCompile(`(?s)<[^>]*>`)
)

type Article struct {
	Title               string      `json:"title"`
	URL                 string      `json:"url"`
	Paragraphs          []Paragraph `json:"paragraphs"`
	PublicVerified      bool        `json:"publicVerified"`
	VerificationPending bool        `json:"verificationPending"`
}

type Paragraph struct {
	Text string `json:"text"`
	Tag  string `json:"tag"`
}

type Grant struct {
	Token      string
	Provider   string
	Paragraphs []string
	Texts      map[string]bool
}

type Service struct {
	mu      sync.Mutex
	client  *http.Client
	current *Article
	pending chan struct{}
	grant   *Grant
}

func New() *Service {
	return &Service{
		client: &http.Client{
			Transport: &http.Transport{Proxy: http.ProxyFromEnvironment},
			Timeout:   12 * time.Second,
		},
	}
}

func Compact(value string) string {
	return strings.Join(strings.Fields(value), " ")
}

func PublicTextMatches(htmlText string, paragraphs []Paragraph) bool {
	if len(paragraphs) == 0 {
		return false
	}
	plain := scriptPattern.ReplaceAllString(htmlText, " ")
	plain = stylePattern.ReplaceAllString(plain, " ")
	plain = tagPattern.ReplaceAllString(plain, " ")
	plain = html.UnescapeString(plain)
	plain = strings.ReplaceAll(plain, "\u00a0", " ")
	plain = Compact(plain)
	for _, paragraph := range paragraphs {
		text := Compact(paragraph.Text)
		if text == "" || !strings.Contains(plain, text) {
			return false
		}
	}
	return true
}

func (s *Service) Reset() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.current = nil
	s.grant = nil
	s.pending = nil
}

func (s *Service) Verify(article *Article) {
	s.mu.Lock()
	s.grant = nil
	s.current = article
	article.PublicVerified = false
	article.VerificationPending = true
	done := make(chan struct{})
	s.pending = done
	s.mu.Unlock()

	go func() {
		defer func() {
			s.mu.Lock()
			article.VerificationPending = false
			if s.current == article {
				s.pending = nil
			}
			s.mu.Unlock()
			close(done)
		}()
		parsed, err := url.Parse(article.URL)
		if err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.User != nil {
			return
		}
		request, err := http.NewRequestWithContext(context.Background(), http.MethodGet, article.URL, nil)
		if err != nil {
			return
		}
		request.Header.Set("User-Agent", "LumenReader/1.0 (public verification)")
		request.Header.Set("Cache-Control", "no-store")
		response, err := s.client.Do(request)
		if err != nil {
			return
		}
		defer response.Body.Close()
		if response.StatusCode < 200 || response.StatusCode >= 300 {
			return
		}
		data, err := io.ReadAll(io.LimitReader(response.Body, 6_000_001))
		if err != nil || len(data) > 6_000_000 {
			return
		}
		verified := PublicTextMatches(string(data), article.Paragraphs)
		s.mu.Lock()
		if s.current == article {
			article.PublicVerified = verified
		}
		s.mu.Unlock()
	}()
}

func (s *Service) Authorize(ctx context.Context, provider string, confirm func(article Article, provider string) (bool, error)) (map[string]any, error) {
	s.mu.Lock()
	article := s.current
	pending := s.pending
	s.mu.Unlock()
	if article == nil {
		return nil, fmt.Errorf("请重新打开双语精读")
	}
	if pending != nil {
		select {
		case <-pending:
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
	s.mu.Lock()
	if s.current != article {
		s.mu.Unlock()
		return nil, fmt.Errorf("文章已切换，请重新进入双语精读")
	}
	publicVerified := article.PublicVerified
	s.mu.Unlock()

	if !publicVerified && confirm != nil {
		allowed, err := confirm(*article, provider)
		if err != nil {
			return nil, err
		}
		if !allowed {
			return nil, nil
		}
	}

	token := randomToken()
	texts := make([]string, 0, len(article.Paragraphs))
	set := map[string]bool{}
	for _, paragraph := range article.Paragraphs {
		texts = append(texts, paragraph.Text)
		set[paragraph.Text] = true
	}
	s.mu.Lock()
	s.grant = &Grant{Token: token, Provider: provider, Paragraphs: texts, Texts: set}
	s.mu.Unlock()
	return map[string]any{"token": token, "publicVerified": publicVerified}, nil
}

func (s *Service) Validate(payload map[string]any, provider string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.grant == nil || s.current == nil {
		return fmt.Errorf("自动翻译授权已失效，请重新进入精读。")
	}
	token, _ := payload["authorization"].(string)
	text, _ := payload["text"].(string)
	contextText, _ := payload["context"].(string)
	if token == "" || token != s.grant.Token || provider != s.grant.Provider || !s.grant.Texts[text] {
		return fmt.Errorf("自动翻译授权已失效，请重新进入精读。")
	}
	index := -1
	for i, value := range s.grant.Paragraphs {
		if value == text {
			index = i
			break
		}
	}
	if index < 0 {
		return fmt.Errorf("自动翻译授权已失效，请重新进入精读。")
	}
	start := max(0, index-1)
	end := min(len(s.grant.Paragraphs), index+2)
	expected := strings.Join(s.grant.Paragraphs[start:end], "\n")
	if contextText != "" && contextText != expected {
		return fmt.Errorf("段落语境与授权文章不符")
	}
	return nil
}

func (s *Service) Restore(article *Article) {
	s.Verify(article)
}

func randomToken() string {
	buffer := make([]byte, 16)
	if _, err := rand.Read(buffer); err != nil {
		return fmt.Sprintf("%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(buffer)
}
