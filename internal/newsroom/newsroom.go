package newsroom

import (
	"context"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/PuerkitoBio/goquery"

	"lumen/internal/feed"
	"lumen/internal/model"
	"lumen/internal/rank"
)

var (
	skipTitle        = regexp.MustCompile(`(?i)^(read more|learn more|view all|sign in|subscribe|latest news|all news|news and events|see all)`)
	specificTitles   = regexp.MustCompile(`(?i)^(board of governors of the federal reserve system|large hadron collider|higgs boson|antimatter)$`)
	skipSection      = regexp.MustCompile(`(?i)/(?:topics?|tags?|authors?|categories|search|subscribe|login|privacy|about|aboutthefed|bios|what-we-do|who-we-are|our-work|contact|careers|events)\b`)
	institutionStory = regexp.MustCompile(`(?i)news|press|release|stories|story|article|blog|insight|publication|publ/|speech|update|\d{4}`)
	storyShape       = regexp.MustCompile(`\d{4}|/article|/story|/news/.+|/press`)
	urlLabelPattern  = regexp.MustCompile(`(?i)^(https?:|www\.)`)
	navSelector      = "nav,footer,header[role=\"banner\"],[role=\"navigation\"],.menu,.nav,.breadcrumb,.social,.advertisement"
	institutions     = map[string]bool{
		"cern": true, "esa": true, "nist": true, "caltech": true, "berkeley": true, "uchicago": true,
		"imperial": true, "eth": true, "maxplanck": true, "csiro": true, "rik": true, "nus": true,
		"ntu": true, "universitytokyo": true, "fed": true, "ecb": true, "bis": true, "noaa": true,
		"copernicus": true, "mit": true, "harvard": true, "stanford": true, "oxford": true,
		"cambridge": true, "royalsociety": true,
	}
)

func DefaultRule() model.NewsroomRule {
	return model.NewsroomRule{
		ExcludeSelector: "nav,footer,header[role=\"banner\"],[role=\"navigation\"],.menu,.nav,.breadcrumb,.social,.advertisement",
		HeadingSelector: "h1,h2,h3,h4",
		TitleMin:        20, TitleMax: 260,
		DenyPathPattern:        "(?i)/(?:topics?|tags?|authors?|categories|search|subscribe|login|privacy|about|aboutthefed|bios|what-we-do|who-we-are|our-work|contact|careers|events)\\b",
		DenyTitlePattern:       "(?i)^(read more|learn more|view all|sign in|subscribe|latest news|all news|news and events|see all)",
		DenyTitles:             []string{"Board of Governors of the Federal Reserve System", "Large Hadron Collider", "Higgs boson", "Antimatter"},
		InstitutionPathPattern: "(?i)news|press|release|stories|story|article|blog|insight|publication|publ/|speech|update|\\d{4}",
		StoryPathPattern:       "\\d{4}|/article|/story|/news/.+|/press",
		MaxItems:               3,
	}
}

func HomepageHeadlines(html string, source model.Source, fetchedAt int64) []model.Article {
	return HomepageHeadlinesWithRule(html, source, DefaultRule(), nil, fetchedAt)
}

func HomepageHeadlinesWithRule(html string, source model.Source, rule model.NewsroomRule, spec *model.NewsroomSpec, fetchedAt int64) []model.Article {
	document, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil
	}
	document.Find("script,style").Remove()
	base, err := url.Parse(source.URL)
	if err != nil {
		return nil
	}
	rule = normalizeRule(rule)
	maxItems := rule.MaxItems
	excludeSelector := rule.ExcludeSelector
	headingSelector := rule.HeadingSelector
	denyPath := mustRegexp(rule.DenyPathPattern)
	denyTitle := mustRegexp(rule.DenyTitlePattern)
	institutionPath := mustRegexp(rule.InstitutionPathPattern)
	storyPath := mustRegexp(rule.StoryPathPattern)
	denyTitles := map[string]bool{}
	for _, title := range rule.DenyTitles {
		denyTitles[strings.ToLower(title)] = true
	}
	institution := source.Region == "国际机构" || contains(specInstitutionSources(spec), source.ID)
	seen := map[string]bool{}
	items := []model.Article{}
	document.Find("a[href]").EachWithBreak(func(_ int, anchor *goquery.Selection) bool {
		if excludeSelector != "" && anchor.Closest(excludeSelector).Length() > 0 {
			return true
		}
		heading := anchor.Find(headingSelector).First()
		if heading.Length() == 0 {
			heading = anchor.Closest(headingSelector)
		}
		title := ""
		candidates := []string{}
		if heading.Length() > 0 {
			candidates = append(candidates, heading.Text())
		}
		candidates = append(candidates, anchor.Text())
		if label, ok := anchor.Attr("aria-label"); ok {
			candidates = append(candidates, label)
		}
		for _, candidate := range candidates {
			candidate = strings.Join(strings.Fields(candidate), " ")
			if len([]rune(candidate)) >= rule.TitleMin && len([]rune(candidate)) <= rule.TitleMax &&
				!urlLabelPattern.MatchString(candidate) {
				title = candidate
				break
			}
		}
		if title == "" || denyTitle.MatchString(title) || denyTitles[strings.ToLower(title)] {
			return true
		}
		href, ok := anchor.Attr("href")
		if !ok {
			return true
		}
		target, err := base.Parse(href)
		if err != nil {
			return true
		}
		if _, ok := rank.SafeURL(target.String()); !ok {
			return true
		}
		if strings.TrimPrefix(target.Hostname(), "www.") != strings.TrimPrefix(base.Hostname(), "www.") {
			return true
		}
		target.Fragment = ""
		path := target.Path
		if path == "" {
			path = "/"
		}
		if path == base.Path || path == "/" || denyPath.MatchString(path) {
			return true
		}
		if institution && !institutionPath.MatchString(path) {
			return true
		}
		inArticle := anchor.Closest("article").Length() > 0
		if heading.Length() == 0 && !inArticle && !storyPath.MatchString(path) {
			return true
		}
		key := target.Scheme + "://" + target.Host + path
		if seen[key] {
			return true
		}
		seen[key] = true
		article := rank.Normalize(rank.NormalizeInput{Title: title, Link: target.String()}, source, fetchedAt)
		if article != nil {
			items = append(items, *article)
		}
		return len(items) < maxItems
	})
	return items
}

func specInstitutionSources(spec *model.NewsroomSpec) []string {
	if spec == nil {
		return nil
	}
	return spec.InstitutionSources
}

func contains(values []string, value string) bool {
	for _, item := range values {
		if item == value {
			return true
		}
	}
	return false
}

func normalizeRule(rule model.NewsroomRule) model.NewsroomRule {
	defaults := DefaultRule()
	if rule.ExcludeSelector == "" {
		rule.ExcludeSelector = defaults.ExcludeSelector
	}
	if rule.HeadingSelector == "" {
		rule.HeadingSelector = defaults.HeadingSelector
	}
	if rule.TitleMin <= 0 {
		rule.TitleMin = defaults.TitleMin
	}
	if rule.TitleMax <= 0 {
		rule.TitleMax = defaults.TitleMax
	}
	if rule.DenyPathPattern == "" {
		rule.DenyPathPattern = defaults.DenyPathPattern
	}
	if rule.DenyTitlePattern == "" {
		rule.DenyTitlePattern = defaults.DenyTitlePattern
	}
	if rule.InstitutionPathPattern == "" {
		rule.InstitutionPathPattern = defaults.InstitutionPathPattern
	}
	if rule.StoryPathPattern == "" {
		rule.StoryPathPattern = defaults.StoryPathPattern
	}
	if rule.MaxItems <= 0 {
		rule.MaxItems = defaults.MaxItems
	}
	return rule
}

func mustRegexp(pattern string) *regexp.Regexp {
	if pattern == "" {
		return regexp.MustCompile("$^")
	}
	compiled, err := regexp.Compile(pattern)
	if err != nil {
		return regexp.MustCompile("$^")
	}
	return compiled
}

type Options struct {
	Sources     []model.Source
	Fetch       func(ctx context.Context, url string, timeout time.Duration) ([]byte, error)
	FeedItems   func(sourceID string) []model.Article
	Previous    map[string]model.NewsroomGroup
	OnProgress  func(done, total int, name string)
	Concurrency int
	Spec        *model.NewsroomSpec
}

func Update(ctx context.Context, options Options) model.Newsroom {
	concurrency := options.Concurrency
	if concurrency <= 0 {
		concurrency = 6
	}
	started := time.Now().UnixMilli()
	queue := make(chan model.Source)
	results := make(chan model.NewsroomGroup, len(options.Sources))
	var wg sync.WaitGroup
	var doneMu sync.Mutex
	done := 0
	total := len(options.Sources)

	for i := 0; i < concurrency; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for source := range queue {
				group := fetchGroup(ctx, source, options)
				results <- group
				doneMu.Lock()
				done++
				if options.OnProgress != nil {
					options.OnProgress(done, total, source.Name)
				}
				doneMu.Unlock()
			}
		}()
	}
	go func() {
		for _, source := range options.Sources {
			queue <- source
		}
		close(queue)
		wg.Wait()
		close(results)
	}()

	bySource := map[string]model.NewsroomGroup{}
	for group := range results {
		bySource[group.SourceID] = group
	}
	groups := make([]model.NewsroomGroup, 0, len(options.Sources))
	covered := 0
	stories := 0
	for _, source := range options.Sources {
		group, ok := bySource[source.ID]
		if !ok {
			continue
		}
		if group.Items == nil {
			group.Items = []model.Article{}
		}
		if len(group.Items) > 0 {
			covered++
			stories += len(group.Items)
		}
		groups = append(groups, group)
	}
	return model.Newsroom{
		UpdatedAt:    started,
		Groups:       groups,
		SourceCount:  len(options.Sources),
		CoveredCount: covered,
		StoryCount:   stories,
	}
}

func fetchGroup(ctx context.Context, source model.Source, options Options) model.NewsroomGroup {
	checkedAt := time.Now().UnixMilli()
	items := []model.Article{}
	basis := "homepage"
	errorText := ""

	data, fetchErr := options.Fetch(ctx, source.URL, 11*time.Second)
	if fetchErr != nil {
		errorText = feed.ErrorMessage(fetchErr)
	} else {
		rule := DefaultRule()
		if options.Spec != nil {
			rule = options.Spec.Default
			if override, ok := options.Spec.Overrides[source.ID]; ok {
				rule = override
			}
			rule = normalizeRule(rule)
		}
		items = HomepageHeadlinesWithRule(string(data), source, rule, options.Spec, checkedAt)
	}

	if len(items) < 3 && source.Feed != "" {
		var feedItems []model.Article
		if options.FeedItems != nil {
			feedItems = options.FeedItems(source.ID)
		}
		feedErr := error(nil)
		if len(feedItems) == 0 {
			feedData, err := options.Fetch(ctx, source.Feed, 11*time.Second)
			if err != nil {
				feedErr = err
			} else {
				feedItems, feedErr = feed.Parse(feedData, source, checkedAt, 3)
			}
		}
		if feedErr == nil && len(feedItems) > len(items) {
			if len(feedItems) > 3 {
				feedItems = feedItems[:3]
			}
			items = feedItems
			basis = "feed"
			errorText = ""
		} else if feedErr != nil && len(items) == 0 {
			errorText = feed.ErrorMessage(feedErr)
		}
	}

	if len(items) > 0 {
		now := time.Now().UnixMilli()
		return model.NewsroomGroup{
			SourceID:  source.ID,
			Basis:     &basis,
			Items:     items,
			CheckedAt: checkedAt,
			FetchedAt: &now,
			Stale:     false,
			Error:     nil,
		}
	}
	if previous, ok := options.Previous[source.ID]; ok && len(previous.Items) > 0 {
		message := errorText
		if message == "" {
			message = "未识别到正文链接"
		}
		previous.CheckedAt = checkedAt
		previous.Stale = true
		previous.Error = &message
		return previous
	}
	message := errorText
	if message == "" {
		message = "首页需要动态加载或未找到文章入口"
	}
	return model.NewsroomGroup{
		SourceID:  source.ID,
		Basis:     nil,
		Items:     []model.Article{},
		CheckedAt: checkedAt,
		FetchedAt: nil,
		Stale:     false,
		Error:     &message,
	}
}
