package rank

import (
	"testing"
	"time"

	"lumen/internal/model"
)

func article(id, source string, extra func(*model.Article)) model.Article {
	now := time.Date(2026, 10, 23, 0, 0, 0, 0, time.UTC).UnixMilli()
	value := model.Article{
		ID: id, Title: "How quantum research changes energy technology",
		URL: "https://example.com/" + id, SourceID: source, Source: source,
		Category: "科技", Reach: 5,
		Description: "Evidence and scientific research. Evidence and scientific research. Evidence and scientific research. Evidence and scientific research. Evidence and scientific research. Evidence and scientific research. Evidence and scientific research. Evidence and scientific research. ",
		PublishedAt: &now,
	}
	if extra != nil {
		extra(&value)
	}
	return value
}

func TestSafeURL(t *testing.T) {
	for _, value := range []string{"javascript:alert(1)", "file:///C:/x", "data:text/html,x", "https://a:b@example.com"} {
		if _, ok := SafeURL(value); ok {
			t.Fatalf("unsafe URL accepted: %s", value)
		}
	}
	if _, ok := SafeURL("https://example.com/a"); !ok {
		t.Fatal("safe URL rejected")
	}
}

func TestQualitySignals(t *testing.T) {
	now := time.Now().UnixMilli()
	deep := QualitySignals(model.Article{Title: "Analysis: Why climate research matters for public policy", Description: "A study uses survey data, although limitations remain. A study uses survey data, although limitations remain. A study uses survey data, although limitations remain. A study uses survey data, although limitations remain. "})
	quick := QualitySignals(model.Article{Title: "Live updates: shocking deals buy now"})
	if deep.Score <= quick.Score+30 {
		t.Fatalf("expected deep quality much higher: %d vs %d", deep.Score, quick.Score)
	}
	preprint := QualitySignals(model.Article{Title: "Quantum research", SourceID: "arxiv", PublishedAt: &now})
	found := false
	for _, reason := range preprint.Reasons {
		if reason == "预印本，同行评审状态未核验" {
			found = true
		}
	}
	if !found {
		t.Fatal("missing preprint reason")
	}
}

func TestRankingFreshnessBuzzAndDiversity(t *testing.T) {
	now := time.Date(2026, 10, 23, 0, 0, 0, 0, time.UTC).UnixMilli()
	old := now - 20*86400000
	future := now + 86400000
	articles := []model.Article{
		article("old", "bbc", func(a *model.Article) { a.PublishedAt = &old }),
		article("new", "bbc", nil),
		article("future", "bbc", func(a *model.Article) { a.PublishedAt = &future }),
	}
	weights := model.DefaultWeights
	weights.Freshness = 100
	weights.Reach, weights.Buzz, weights.Frontier, weights.Learning = 0, 0, 0, 0
	result := Rank(articles, weights, now, nil)
	if len(result) != 3 || result[0].ID != "new" {
		t.Fatalf("unexpected ranking order: %+v", result)
	}
	for _, item := range result {
		if item.ID == "future" && item.Metrics.Freshness != 20 {
			t.Fatalf("future freshness = %d", item.Metrics.Freshness)
		}
	}

	buzzArticles := []model.Article{
		article("a", "bbc", nil),
		article("b", "reuters", nil),
		article("c", "nature", nil),
		article("d", "guardian", nil),
	}
	ranked := Rank(buzzArticles, model.DefaultWeights, now, nil)
	for _, item := range ranked {
		if item.ID == "a" && item.PeerCount != 3 {
			t.Fatalf("peer count for a = %d, want 3", item.PeerCount)
		}
	}
	firstSources := map[string]bool{}
	for i := 0; i < 3; i++ {
		firstSources[ranked[i].SourceID] = true
	}
	if len(firstSources) < 3 {
		t.Fatalf("diversity failed: %v", firstSources)
	}
}

func TestMergeDeduplicates(t *testing.T) {
	items := []model.Article{article("a", "bbc", nil)}
	if got := len(Merge(items, items)); got != 1 {
		t.Fatalf("merge length = %d", got)
	}
}

func TestReviewCard(t *testing.T) {
	now := time.Now().UnixMilli()
	if got := ReviewCard(model.Vocab{Level: 1}, true, now).DueAt; got != now+3*86400000 {
		t.Fatalf("known due = %d", got)
	}
	if got := ReviewCard(model.Vocab{Level: 4}, false, now).DueAt; got != now+600000 {
		t.Fatalf("lapse due = %d", got)
	}
}

func TestCSVInjection(t *testing.T) {
	if value := CSVCell("=1+1"); value[:2] != "\"'" {
		t.Fatalf("formula not neutralized: %s", value)
	}
}
