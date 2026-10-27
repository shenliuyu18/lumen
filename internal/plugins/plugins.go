package plugins

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"lumen/internal/model"
	pluginassets "lumen/plugins"
)

const maxPluginBytes = 32 << 20

var pluginIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)

type AdRuleSet struct {
	Selectors    []string `json:"selectors"`
	TextPatterns []string `json:"textPatterns"`
	BlockHosts   []string `json:"blockHosts"`
}

type AdRules struct {
	Global  AdRuleSet            `json:"global"`
	Domains map[string]AdRuleSet `json:"domains"`
}

type Plugin struct {
	Format      int                 `json:"format"`
	ID          string              `json:"id"`
	Name        string              `json:"name"`
	Version     string              `json:"version"`
	Type        string              `json:"type"`
	Description string              `json:"description"`
	Author      string              `json:"author"`
	License     string              `json:"license"`
	Sources     []model.Source      `json:"sources"`
	Newsroom    *model.NewsroomSpec `json:"newsroom"`
	Rules       *AdRules            `json:"rules"`
}

type Manager struct {
	dir       string
	installed []Plugin
	engine    string
}

func New(dataDir string) (*Manager, error) {
	manager := &Manager{dir: filepath.Join(dataDir, "plugins")}
	if err := os.MkdirAll(manager.dir, 0o755); err != nil {
		return nil, err
	}
	engine, err := pluginassets.Files.ReadFile("runtime/ads-engine.js")
	if err != nil {
		return nil, err
	}
	manager.engine = string(engine)
	if err := manager.loadInstalled(); err != nil {
		return nil, err
	}
	return manager, nil
}

func (m *Manager) Dir() string { return m.dir }

func (m *Manager) loadInstalled() error {
	m.installed = nil
	entries, err := os.ReadDir(m.dir)
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if !entry.IsDir() || strings.HasPrefix(entry.Name(), ".install-") {
			continue
		}
		data, err := os.ReadFile(filepath.Join(m.dir, entry.Name(), "plugin.json"))
		if err != nil {
			continue
		}
		plugin, err := decodePlugin(data)
		if err != nil {
			continue
		}
		m.installed = append(m.installed, plugin)
	}
	return nil
}

func (m *Manager) Reload() error {
	return m.loadInstalled()
}

func (m *Manager) all() []Plugin {
	result := append([]Plugin{}, m.installed...)
	sort.SliceStable(result, func(i, j int) bool {
		return strings.ToLower(result[i].Name) < strings.ToLower(result[j].Name)
	})
	return result
}

func (m *Manager) List(disabled []string) []model.PluginInfo {
	disabledSet := disabledSet(disabled)
	infos := make([]model.PluginInfo, 0)
	for _, plugin := range m.all() {
		infos = append(infos, model.PluginInfo{
			ID: plugin.ID, Name: plugin.Name, Version: plugin.Version, Type: plugin.Type,
			Description: plugin.Description, Author: plugin.Author, License: plugin.License,
			Enabled: !disabledSet[plugin.ID], SourceCount: len(plugin.Sources), RuleCount: ruleCount(plugin.Rules),
		})
	}
	return infos
}

func (m *Manager) Sources(disabled []string) []model.Source {
	disabledSet := disabledSet(disabled)
	seen := map[string]bool{}
	result := make([]model.Source, 0)
	for _, plugin := range m.all() {
		if plugin.Type != "crawler" || disabledSet[plugin.ID] {
			continue
		}
		for _, source := range plugin.Sources {
			if source.ID == "" || seen[source.ID] {
				continue
			}
			seen[source.ID] = true
			result = append(result, source)
		}
	}
	return result
}

func (m *Manager) NewsroomSpec(disabled []string) *model.NewsroomSpec {
	disabledSet := disabledSet(disabled)
	for _, plugin := range m.all() {
		if plugin.Type == "crawler" && !disabledSet[plugin.ID] && plugin.Newsroom != nil {
			return plugin.Newsroom
		}
	}
	return nil
}

func (m *Manager) AdScript(disabled []string) string {
	disabledSet := disabledSet(disabled)
	merged := AdRules{Global: AdRuleSet{}, Domains: map[string]AdRuleSet{}}
	found := false
	for _, plugin := range m.all() {
		if plugin.Type != "adblock" || disabledSet[plugin.ID] || plugin.Rules == nil {
			continue
		}
		found = true
		merged.Global.Selectors = append(merged.Global.Selectors, plugin.Rules.Global.Selectors...)
		merged.Global.TextPatterns = append(merged.Global.TextPatterns, plugin.Rules.Global.TextPatterns...)
		merged.Global.BlockHosts = append(merged.Global.BlockHosts, plugin.Rules.Global.BlockHosts...)
		for domain, rules := range plugin.Rules.Domains {
			current := merged.Domains[domain]
			current.Selectors = append(current.Selectors, rules.Selectors...)
			current.TextPatterns = append(current.TextPatterns, rules.TextPatterns...)
			merged.Domains[domain] = current
		}
	}
	if !found {
		return ""
	}
	merged.Global.Selectors = unique(merged.Global.Selectors)
	merged.Global.TextPatterns = unique(merged.Global.TextPatterns)
	for domain, rules := range merged.Domains {
		rules.Selectors = unique(rules.Selectors)
		rules.TextPatterns = unique(rules.TextPatterns)
		merged.Domains[domain] = rules
	}
	payload, _ := json.Marshal(merged)
	return "window.__LUMEN_AD_RULES__ = " + string(payload) + ";\n" + m.engine
}

// BlockHosts 汇总已启用去广告插件的域名级屏蔽表，供 WebView 请求拦截使用。
func (m *Manager) BlockHosts(disabled []string) map[string]bool {
	disabledSet := disabledSet(disabled)
	hosts := map[string]bool{}
	for _, plugin := range m.all() {
		if plugin.Type != "adblock" || disabledSet[plugin.ID] || plugin.Rules == nil {
			continue
		}
		for _, host := range plugin.Rules.Global.BlockHosts {
			host = strings.TrimPrefix(strings.ToLower(strings.TrimSpace(host)), "www.")
			if host != "" {
				hosts[host] = true
			}
		}
	}
	return hosts
}

// Install accepts a ZIP package or a declarative JS module. JS files are parsed,
// never executed: module.exports / export default / window.__LUMEN_PLUGIN__
// must wrap a JSON plugin object.
func (m *Manager) Install(path string) (model.PluginInfo, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return model.PluginInfo{}, err
	}
	if len(data) == 0 || len(data) > maxPluginBytes {
		return model.PluginInfo{}, fmt.Errorf("插件包为空或过大")
	}
	if isZipData(data) {
		return m.installZip(data)
	}
	plugin, err := parseJSPlugin(data)
	if err != nil {
		return model.PluginInfo{}, err
	}
	if err := m.installPlugin(plugin); err != nil {
		return model.PluginInfo{}, err
	}
	return m.info(plugin.ID), nil
}

func (m *Manager) installZip(data []byte) (model.PluginInfo, error) {
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return model.PluginInfo{}, fmt.Errorf("无法读取 ZIP 插件：%w", err)
	}
	var totalBytes uint64
	manifestPath := ""
	for _, file := range reader.File {
		totalBytes += file.UncompressedSize64
		if totalBytes > maxPluginBytes {
			return model.PluginInfo{}, fmt.Errorf("插件包解压后过大")
		}
		clean := filepath.ToSlash(filepath.Clean(file.Name))
		if safeZipName(clean) && strings.HasSuffix(clean, "plugin.json") && strings.Count(clean, "/") <= 1 {
			manifestPath = file.Name
			break
		}
	}
	if manifestPath == "" {
		return model.PluginInfo{}, fmt.Errorf("插件包缺少 plugin.json")
	}
	baseDir := filepath.ToSlash(filepath.Dir(manifestPath))
	temp, err := os.MkdirTemp(m.dir, ".install-*")
	if err != nil {
		return model.PluginInfo{}, err
	}
	defer os.RemoveAll(temp)
	for _, file := range reader.File {
		clean := filepath.ToSlash(filepath.Clean(file.Name))
		if !safeZipName(clean) {
			continue
		}
		if baseDir != "." && !strings.HasPrefix(clean, baseDir) {
			continue
		}
		relative := strings.TrimPrefix(strings.TrimPrefix(clean, baseDir), "/")
		if relative == "" || !safeZipName(relative) {
			continue
		}
		target := filepath.Join(temp, filepath.FromSlash(relative))
		if !strings.HasPrefix(filepath.Clean(target), filepath.Clean(temp)+string(os.PathSeparator)) {
			continue
		}
		if file.FileInfo().IsDir() {
			_ = os.MkdirAll(target, 0o755)
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return model.PluginInfo{}, err
		}
		source, err := file.Open()
		if err != nil {
			return model.PluginInfo{}, err
		}
		destination, err := os.Create(target)
		if err != nil {
			source.Close()
			return model.PluginInfo{}, err
		}
		_, copyErr := io.Copy(destination, source)
		source.Close()
		destination.Close()
		if copyErr != nil {
			return model.PluginInfo{}, copyErr
		}
	}
	manifest, err := os.ReadFile(filepath.Join(temp, "plugin.json"))
	if err != nil {
		return model.PluginInfo{}, err
	}
	plugin, err := decodePlugin(manifest)
	if err != nil {
		return model.PluginInfo{}, err
	}
	if err := m.commit(plugin.ID, temp); err != nil {
		return model.PluginInfo{}, err
	}
	return m.info(plugin.ID), nil
}

func (m *Manager) installPlugin(plugin Plugin) error {
	if err := validatePlugin(plugin); err != nil {
		return err
	}
	temp, err := os.MkdirTemp(m.dir, ".install-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(temp)
	data, err := json.MarshalIndent(plugin, "", "  ")
	if err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(temp, "plugin.json"), data, 0o644); err != nil {
		return err
	}
	return m.commit(plugin.ID, temp)
}

func (m *Manager) commit(id, temp string) error {
	target := filepath.Join(m.dir, id)
	if !strings.HasPrefix(filepath.Clean(target), filepath.Clean(m.dir)+string(os.PathSeparator)) {
		return fmt.Errorf("无效插件 ID")
	}
	if err := os.RemoveAll(target); err != nil {
		return err
	}
	if err := os.Rename(temp, target); err != nil {
		return err
	}
	return m.loadInstalled()
}

func (m *Manager) Remove(id string) error {
	path := filepath.Join(m.dir, id)
	if !strings.HasPrefix(filepath.Clean(path), filepath.Clean(m.dir)+string(os.PathSeparator)) {
		return fmt.Errorf("无效插件")
	}
	if err := os.RemoveAll(path); err != nil {
		return err
	}
	return m.loadInstalled()
}

func (m *Manager) info(id string) model.PluginInfo {
	for _, info := range m.List(nil) {
		if info.ID == id {
			return info
		}
	}
	return model.PluginInfo{}
}

func parseJSPlugin(data []byte) (Plugin, error) {
	text := strings.TrimSpace(strings.TrimPrefix(string(data), "\ufeff"))
	start := strings.Index(text, "{")
	end := strings.LastIndex(text, "}")
	if start < 0 || end <= start {
		return Plugin{}, fmt.Errorf("JS 插件需要导出 JSON 对象，例如 module.exports = {...}")
	}
	var plugin Plugin
	if err := json.Unmarshal([]byte(text[start:end+1]), &plugin); err != nil {
		return Plugin{}, fmt.Errorf("JS 插件对象不是合法 JSON：%w", err)
	}
	if err := validatePlugin(plugin); err != nil {
		return Plugin{}, err
	}
	return plugin, nil
}

func isZipData(data []byte) bool {
	return len(data) >= 4 && data[0] == 'P' && data[1] == 'K' &&
		((data[2] == 3 && data[3] == 4) || (data[2] == 5 && data[3] == 6) || (data[2] == 7 && data[3] == 8))
}

func decodePlugin(data []byte) (Plugin, error) {
	var plugin Plugin
	if err := json.Unmarshal(data, &plugin); err != nil {
		return plugin, err
	}
	return plugin, validatePlugin(plugin)
}

func validatePlugin(plugin Plugin) error {
	if plugin.Format != 1 || plugin.ID == "" || plugin.Name == "" {
		return fmt.Errorf("插件格式无效")
	}
	if !pluginIDPattern.MatchString(plugin.ID) {
		return fmt.Errorf("插件 ID 只能包含字母、数字、点、短横线或下划线")
	}
	if plugin.Type != "crawler" && plugin.Type != "adblock" {
		return fmt.Errorf("不支持的插件类型: %s", plugin.Type)
	}
	if plugin.Type == "crawler" && len(plugin.Sources) == 0 {
		return fmt.Errorf("爬虫插件没有来源")
	}
	if plugin.Type == "adblock" && plugin.Rules == nil {
		return fmt.Errorf("去广告插件没有规则")
	}
	return nil
}

func safeZipName(name string) bool {
	if name == "" || strings.HasPrefix(name, "/") || strings.Contains(name, ":") {
		return false
	}
	for _, part := range strings.Split(name, "/") {
		if part == ".." {
			return false
		}
	}
	return true
}

func disabledSet(values []string) map[string]bool {
	set := map[string]bool{}
	for _, value := range values {
		set[value] = true
	}
	return set
}

func ruleCount(rules *AdRules) int {
	if rules == nil {
		return 0
	}
	count := len(rules.Global.Selectors) + len(rules.Global.TextPatterns)
	for _, domain := range rules.Domains {
		count += len(domain.Selectors) + len(domain.TextPatterns)
	}
	return count
}

func unique(values []string) []string {
	seen := map[string]bool{}
	result := make([]string, 0, len(values))
	for _, value := range values {
		if value == "" || seen[value] {
			continue
		}
		seen[value] = true
		result = append(result, value)
	}
	return result
}
