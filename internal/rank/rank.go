package rank

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"html"
	"math"
	"net/url"
	"regexp"
	"sort"
	"strings"

	"lumen/internal/model"
)

var stopWords = map[string]bool{
	"this": true, "that": true, "with": true, "from": true, "have": true, "will": true,
	"what": true, "when": true, "where": true, "which": true, "their": true, "about": true,
	"into": true, "over": true, "after": true, "more": true, "could": true, "would": true,
	"should": true, "says": true, "said": true, "they": true, "them": true, "than": true,
	"your": true, "been": true, "being": true, "also": true, "amid": true, "here": true,
	"through": true, "first": true, "these": true, "those": true, "world": true, "news": true,
	"live": true,
}

var (
	tagPattern      = regexp.MustCompile(`<[^>]*>`)
	wordPattern     = regexp.MustCompile(`[a-z]{4,}`)
	frontierPattern = regexp.MustCompile(`(?i)\b(research|discover\w*|breakthrough|artificial intelligence|AI|quantum|robot\w*|climate|energy|trial|genom\w*|semiconductor\w*|space|innovation|technology|science)\b`)
	learningPattern = regexp.MustCompile(`(?i)\b(why|how|analysis|explained|essay|research|opinion)\b`)
	formulaPattern  = regexp.MustCompile(`^[=+\-@\t\r]`)
)

func SafeURL(value string) (string, bool) {
	parsed, err := url.Parse(strings.TrimSpace(value))
	if err != nil {
		return "", false
	}
	if parsed.Scheme != "https" && parsed.Scheme != "http" {
		return "", false
	}
	if parsed.User != nil || parsed.Host == "" {
		return "", false
	}
	return parsed.String(), true
}

func Clean(value string) string {
	value = tagPattern.ReplaceAllString(value, " ")
	value = html.UnescapeString(value)
	value = strings.ReplaceAll(value, "\u00a0", " ")
	return strings.Join(strings.Fields(value), " ")
}

type NormalizeInput struct {
	Title       string
	Link        string
	Description string
	PublishedAt *int64
}

func Normalize(input NormalizeInput, source model.Source, fetchedAt int64) *model.Article {
	link, ok := SafeURL(input.Link)
	title := Clean(input.Title)
	if !ok || title == "" {
		return nil
	}
	sum := sha256.Sum256([]byte(link))
	description := Clean(input.Description)
	if runes := []rune(description); len(runes) > 360 {
		description = string(runes[:360])
	}
	return &model.Article{
		ID:          hex.EncodeToString(sum[:])[:20],
		Title:       title,
		URL:         link,
		SourceID:    source.ID,
		Source:      source.Name,
		Category:    source.Category,
		Region:      source.Region,
		Reach:       source.Reach,
		Description: description,
		PublishedAt: input.PublishedAt,
		FetchedAt:   fetchedAt,
	}
}

func QualitySignals(a model.Article) model.Quality {
	text := strings.ToLower(a.Title + " " + a.Description)
	score := 45
	reasons := []string{}
	if regexp.MustCompile(`\b(why|how|analysis|explained|investigation|essay|in.depth|what .+ means)\b`).MatchString(text) {
		score += 18
		reasons = append(reasons, "含解释、分析或调查信号")
	}
	if regexp.MustCompile(`\b(study|research|evidence|data|survey|trial|report|peer.review|scientists)\b`).MatchString(text) {
		score += 15
		reasons = append(reasons, "摘要出现研究或数据证据信号")
	}
	if regexp.MustCompile(`\b(climate|health|economy|education|energy|housing|inequality|democracy|policy|trade|artificial intelligence|public|society|war)\b`).MatchString(text) {
		score += 12
		reasons = append(reasons, "具有公共议题关联")
	}
	if regexp.MustCompile(`\b(may|might|uncertain|limits|limitations|suggest|risk|however|although)\b`).MatchString(text) {
		score += 7
		reasons = append(reasons, "摘要包含不确定性或边界表述")
	}
	if len([]rune(a.Description)) >= 160 {
		score += 6
		reasons = append(reasons, "摘要信息较充足")
	}
	if regexp.MustCompile(`\b(live updates?|liveblog|watch live|breaking|minute.by.minute)\b`).MatchString(strings.ToLower(a.Title)) {
		score -= 22
		reasons = append(reasons, "滚动快讯精读降权")
	}
	if regexp.MustCompile(`\b(you won't believe|shocking|must.see|deals|coupon|buy now)\b`).MatchString(text) {
		score -= 25
		reasons = append(reasons, "促销或吸睛措辞降权")
	}
	if a.SourceID == "arxiv" {
		score -= 12
		reasons = append(reasons, "预印本，同行评审状态未核验")
	}
	if len(reasons) == 0 {
		reasons = append(reasons, "标题与摘要不足以判断全文深度")
	}
	score = max(0, min(100, score))
	label := "拓展阅读"
	if score >= 80 {
		label = "优先精读"
	} else if score >= 65 {
		label = "值得深入"
	}
	return model.Quality{Score: score, Reasons: reasons, Label: label, Basis: "标题与摘要启发式信号，非全文事实核查"}
}

func tokens(text string) map[string]bool {
	set := map[string]bool{}
	for _, word := range wordPattern.FindAllString(strings.ToLower(text), -1) {
		if !stopWords[word] {
			set[word] = true
		}
	}
	return set
}

type ranked struct {
	article model.Article
	index   int
}

func Rank(articles []model.Article, weights model.Weights, now int64, interests []string) []model.Article {
	valid := make([]model.Article, 0, len(articles))
	for _, article := range articles {
		if _, ok := SafeURL(article.URL); ok {
			valid = append(valid, article)
		}
	}
	sets := make([]map[string]bool, len(valid))
	postings := map[string][]int{}
	overlap := make([]map[int]int, len(valid))
	for i := range valid {
		sets[i] = tokens(valid[i].Title)
		overlap[i] = map[int]int{}
	}
	for i, set := range sets {
		for word := range set {
			prior := postings[word]
			for _, j := range prior {
				overlap[i][j]++
				overlap[j][i]++
			}
			postings[word] = append(prior, i)
		}
	}

	duplicateNeighbors := make([][]int, len(valid))
	peerCounts := make([]int, len(valid))
	for i := range valid {
		peers := map[string]bool{}
		for j, shared := range overlap[i] {
			denominator := min(len(sets[i]), len(sets[j]))
			if denominator == 0 {
				denominator = 1
			}
			ratio := float64(shared) / float64(denominator)
			if ratio > 0.65 {
				duplicateNeighbors[i] = append(duplicateNeighbors[i], j)
			}
			if valid[i].SourceID != valid[j].SourceID && shared >= 3 && ratio >= 0.38 {
				peers[valid[j].SourceID] = true
			}
		}
		peerCounts[i] = len(peers)
	}

	interestSet := map[string]bool{}
	for _, value := range interests {
		interestSet[value] = true
	}

	items := make([]ranked, 0, len(valid))
	for i, article := range valid {
		var ageHours float64
		if article.PublishedAt != nil {
			ageHours = float64(now-*article.PublishedAt) / 3600000
		}
		freshness := 20
		if article.PublishedAt != nil && ageHours >= -1 {
			window := 60.0
			if article.Category == "学术" {
				window = 168
			}
			freshness = int(math.Round(100 * math.Exp(-math.Max(0, ageHours)/window)))
		}
		buzz := min(100, peerCounts[i]*20)
		frontierMatches := frontierPattern.FindAllString(article.Title+" "+article.Description, -1)
		frontierUnique := map[string]bool{}
		for _, match := range frontierMatches {
			frontierUnique[strings.ToLower(match)] = true
		}
		baseFrontier := 15
		if article.Category == "科技" || article.Category == "学术" {
			baseFrontier = 45
		}
		frontier := min(100, baseFrontier+len(frontierUnique)*15)
		learning := 40
		if len([]rune(article.Description)) > 100 {
			learning += 20
		}
		if learningPattern.MatchString(article.Title) {
			learning += 30
		}
		if len(strings.Fields(article.Title)) >= 8 {
			learning += 10
		}
		learning = min(100, learning)
		reach := article.Reach
		if reach == 0 {
			reach = 3
		}
		metrics := model.Metrics{
			Freshness: freshness,
			Reach:     reach * 20,
			Buzz:      buzz,
			Frontier:  frontier,
			Learning:  learning,
		}
		sumWeights := weights.Freshness + weights.Reach + weights.Buzz + weights.Frontier + weights.Learning
		if sumWeights == 0 {
			sumWeights = 1
		}
		baseScore := float64(metrics.Freshness*weights.Freshness + metrics.Reach*weights.Reach +
			metrics.Buzz*weights.Buzz + metrics.Frontier*weights.Frontier + metrics.Learning*weights.Learning)
		baseScore /= float64(sumWeights)
		quality := QualitySignals(article)
		score := int(math.Round(baseScore*0.8 + float64(quality.Score)*0.2))
		article.Score = score
		article.Quality = &quality
		article.Metrics = &metrics
		article.PeerCount = peerCounts[i]
		article.Interest = 0
		if interestSet[article.Category] {
			article.Interest = 8
		}
		items = append(items, ranked{article: article, index: i})
	}

	sort.SliceStable(items, func(i, j int) bool {
		return items[i].article.Score+items[i].article.Interest > items[j].article.Score+items[j].article.Interest
	})

	result := make([]model.Article, 0, len(items))
	used := map[string]int{}
	cats := map[string]int{}
	duplicated := make([]bool, len(valid))
	for len(items) > 0 {
		best := 0
		bestScore := math.Inf(-1)
		for i, item := range items {
			candidate := float64(item.article.Score+item.article.Interest) -
				float64(used[item.article.SourceID])*13 -
				float64(cats[item.article.Category])*3
			if duplicated[item.index] {
				candidate -= 16
			}
			if candidate > bestScore {
				bestScore = candidate
				best = i
			}
		}
		chosen := items[best]
		items = append(items[:best], items[best+1:]...)
		for _, neighbor := range duplicateNeighbors[chosen.index] {
			duplicated[neighbor] = true
		}
		result = append(result, chosen.article)
		used[chosen.article.SourceID]++
		cats[chosen.article.Category]++
	}
	return result
}

func Merge(oldItems, newItems []model.Article) []model.Article {
	seen := map[string]int{}
	merged := make([]model.Article, 0, len(oldItems)+len(newItems))
	for _, article := range append(append([]model.Article{}, oldItems...), newItems...) {
		if _, ok := SafeURL(article.URL); !ok {
			continue
		}
		if index, exists := seen[article.ID]; exists {
			merged[index] = article
			continue
		}
		seen[article.ID] = len(merged)
		merged = append(merged, article)
	}
	sort.SliceStable(merged, func(i, j int) bool { return merged[i].Published() > merged[j].Published() })
	if len(merged) > 2500 {
		merged = merged[:2500]
	}
	return merged
}

func ReviewCard(card model.Vocab, known bool, now int64) model.Vocab {
	level := 0
	if known {
		level = min(card.Level+1, 6)
	}
	due := now + 600000
	if known {
		days := []int64{1, 1, 3, 7, 14, 30, 60}
		due = now + days[level]*86400000
	}
	card.Level = level
	card.DueAt = due
	card.ReviewedAt = now
	return card
}

func CSVCell(value any) string {
	if value == nil {
		value = ""
	}
	text := fmt.Sprint(value)
	if formulaPattern.MatchString(text) {
		text = "'" + text
	}
	return `"` + strings.ReplaceAll(text, `"`, `""`) + `"`
}
