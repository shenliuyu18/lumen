// Command import-adblock 把 ABP/uBlock 规则中的「外观过滤」转换成 Lumen 插件可用的 CSS 选择器。
//
// 用法：go run ./cmd/import-adblock -in adblock_auto.txt [-plugin plugins/lumen-adblock/plugin.json]
//
// 只保留 selector 形态的规则：网络拦截规则（$option）与脚本注入（##+js）无法在 WebView
// 的 DOM 清理引擎里执行。通用选择器做了数量上限，避免每次 DOM 变动都跑上万次查询。
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"regexp"
	"sort"
	"strings"
)

type Plugin struct {
	Format      int    `json:"format"`
	ID          string `json:"id"`
	Name        string `json:"name"`
	Version     string `json:"version"`
	Type        string `json:"type"`
	Description string `json:"description"`
	Author      string `json:"author"`
	License     string `json:"license"`
	Rules       Rules  `json:"rules"`
}

type Rules struct {
	Global  GlobalRule             `json:"global"`
	Domains map[string]DomainRule  `json:"domains"`
}

type GlobalRule struct {
	Selectors    []string `json:"selectors"`
	TextPatterns []string `json:"textPatterns"`
	BlockHosts   []string `json:"blockHosts,omitempty"`
}

type DomainRule struct {
	Selectors    []string `json:"selectors,omitempty"`
	TextPatterns []string `json:"textPatterns,omitempty"`
}

type Crawler struct {
	Sources []struct {
		URL  string `json:"url"`
		Feed string `json:"feed"`
	} `json:"sources"`
}

var hostPattern = regexp.MustCompile(`^https?://([^/]+)`)

// 只接受简单选择器：出现这些片段一律丢弃，引擎无法安全执行。
var rejected = []string{":has(", ":has-text(", ":contains(", ":style(", ":matches(", ":matches-attr(", ":upward(", ":remove(", "##", "#@#", "$$", "{", "}", "\\"}

func simpleSelector(selector string) bool {
	selector = strings.TrimSpace(selector)
	if selector == "" || len(selector) > 180 {
		return false
	}
	for _, bad := range rejected {
		if strings.Contains(selector, bad) {
			return false
		}
	}
	for _, r := range selector {
		switch {
		case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9':
		case strings.ContainsRune(" .#[]=^$*~>+:_()-|\"'", r):
		default:
			return false
		}
	}
	return strings.ContainsAny(selector, ".#[")
}

func main() {
	in := flag.String("in", "", "adblock_auto.txt 路径")
	pluginPath := flag.String("plugin", "plugins/lumen-adblock/plugin.json", "要合并的插件文件")
	crawlerPath := flag.String("crawler", "plugins/lumen-crawler/plugin.json", "用于取源站域名的爬虫插件")
	maxGeneric := flag.Int("max-generic", 600, "通用选择器上限")
	maxPerDomain := flag.Int("max-domain", 40, "单个域名选择器上限")
	flag.Parse()
	if *in == "" {
		fmt.Fprintln(os.Stderr, "缺少 -in")
		os.Exit(2)
	}

	hosts := map[string]bool{}
	if raw, err := os.ReadFile(*crawlerPath); err == nil {
		var crawler Crawler
		if json.Unmarshal(raw, &crawler) == nil {
			for _, source := range crawler.Sources {
				for _, link := range []string{source.URL, source.Feed} {
					if match := hostPattern.FindStringSubmatch(link); len(match) == 2 {
						host := strings.TrimPrefix(strings.ToLower(match[1]), "www.")
						hosts[host] = true
						if dot := strings.Index(host, "."); dot > 0 {
							hosts[host[dot+1:]] = true
						}
					}
				}
			}
		}
	}

	raw, err := os.ReadFile(*in)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	blockHosts := map[string]bool{}
	generic := map[string]bool{}
	domainRules := map[string]map[string]bool{}
	for _, line := range strings.Split(string(raw), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "!") {
			continue
		}
		if !strings.Contains(line, "##") {
			if host := blockedHost(line); host != "" {
				blockHosts[host] = true
			}
			continue
		}
		parts := strings.SplitN(line, "##", 2)
		domains, selector := parts[0], parts[1]
		if !simpleSelector(selector) {
			continue
		}
		if domains == "" {
			generic[selector] = true
			continue
		}
		for _, domain := range strings.Split(domains, ",") {
			domain = strings.TrimPrefix(strings.TrimSpace(strings.TrimPrefix(domain, "~")), "www.")
			if domain == "" || !hosts[domain] {
				continue
			}
			if domainRules[domain] == nil {
				domainRules[domain] = map[string]bool{}
			}
			domainRules[domain][selector] = true
		}
	}

	pluginRaw, err := os.ReadFile(*pluginPath)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	var plugin Plugin
	if err := json.Unmarshal(pluginRaw, &plugin); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	if plugin.Rules.Domains == nil {
		plugin.Rules.Domains = map[string]DomainRule{}
	}

	// 合并通用选择器：保留已有条目，新条目按长度排序后截断，优先保留更具体的选择器。
	seen := map[string]bool{}
	for _, selector := range plugin.Rules.Global.Selectors {
		seen[selector] = true
	}
	fresh := make([]string, 0, len(generic))
	for selector := range generic {
		if !seen[selector] {
			fresh = append(fresh, selector)
		}
	}
	sort.Slice(fresh, func(i, j int) bool {
		if len(fresh[i]) != len(fresh[j]) {
			return len(fresh[i]) > len(fresh[j])
		}
		return fresh[i] < fresh[j]
	})
	room := *maxGeneric - len(plugin.Rules.Global.Selectors)
	if room < 0 {
		room = 0
	}
	if len(fresh) > room {
		fresh = fresh[:room]
	}
	sort.Strings(fresh)
	plugin.Rules.Global.Selectors = append(plugin.Rules.Global.Selectors, fresh...)

	added := 0
	for domain, selectors := range domainRules {
		rule := plugin.Rules.Domains[domain]
		existing := map[string]bool{}
		for _, selector := range rule.Selectors {
			existing[selector] = true
		}
		list := make([]string, 0, len(selectors))
		for selector := range selectors {
			if !existing[selector] {
				list = append(list, selector)
			}
		}
		sort.Slice(list, func(i, j int) bool { return len(list[i]) > len(list[j]) })
		if len(list) > *maxPerDomain {
			list = list[:*maxPerDomain]
		}
		if len(list) == 0 {
			continue
		}
		rule.Selectors = append(rule.Selectors, list...)
		plugin.Rules.Domains[domain] = rule
		added += len(list)
	}

	hostsSeen := map[string]bool{}
	for _, host := range plugin.Rules.Global.BlockHosts {
		hostsSeen[host] = true
	}
	addedHosts := make([]string, 0, len(blockHosts))
	for host := range blockHosts {
		if !hostsSeen[host] {
			addedHosts = append(addedHosts, host)
		}
	}
	sort.Strings(addedHosts)
	plugin.Rules.Global.BlockHosts = addedHosts

	plugin.Version = bumpMinor(plugin.Version)
	plugin.Description = "针对 Lumen 官方来源优化的广告识别与清理规则；通用选择器合并自 adblock_auto（lingeringsound/adblock_auto，GPL-3.0）的外观过滤子集。"

	encoded, err := json.MarshalIndent(plugin, "", "  ")
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	if err := os.WriteFile(*pluginPath, append(encoded, '\n'), 0o644); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	fmt.Printf("通用选择器 +%d（共 %d），域名选择器 +%d（共 %d 个域名），版本 %s\n",
		len(fresh), len(plugin.Rules.Global.Selectors), added, len(plugin.Rules.Domains), plugin.Version)
}

func bumpMinor(version string) string {
	parts := strings.Split(strings.TrimSpace(version), ".")
	if len(parts) < 2 {
		return "1.1.0"
	}
	minor := 0
	fmt.Sscanf(parts[1], "%d", &minor)
	return fmt.Sprintf("%s.%d.0", parts[0], minor+1)
}

// blockedHost 从网络规则里取整域屏蔽：||example.com^ 这类。
func blockedHost(line string) string {
	if !strings.HasPrefix(line, "||") {
		return ""
	}
	rest := line[2:]
	end := strings.IndexAny(rest, "^/$|")
	if end <= 0 {
		return ""
	}
	host := strings.ToLower(rest[:end])
	if strings.ContainsAny(host, "*=|") || !strings.Contains(host, ".") || len(host) > 120 {
		return ""
	}
	return host
}
