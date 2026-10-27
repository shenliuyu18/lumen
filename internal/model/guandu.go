// 观读轻译（guandu-lite）1.4.1 的显示与提问窗口配置，字段名与插件保持一致。
package model

import (
	"math"
	"strings"
)

type DisplaySettings struct {
	FontScale     float64 `json:"fontScale"`
	FontPreset    string  `json:"fontPreset"`
	LineHeight    float64 `json:"lineHeight"`
	Style         string  `json:"style"`
	Theme         string  `json:"theme"`
	Layout        string  `json:"layout"`
	Palette       string  `json:"palette"`
	FontWeight    int     `json:"fontWeight"`
	TextAlign     string  `json:"textAlign"`
	Density       string  `json:"density"`
	LetterSpacing float64 `json:"letterSpacing"`
	TextIndent    float64 `json:"textIndent"`
	CornerRadius  float64 `json:"cornerRadius"`
	TextColor     string  `json:"textColor"`
	Background    string  `json:"backgroundColor"`
	Border        string  `json:"borderColor"`
}

func DefaultDisplaySettings() DisplaySettings {
	return DisplaySettings{
		FontScale: 1, FontPreset: "reading", LineHeight: 1.9, Style: "card", Theme: "auto",
		Layout: "below", Palette: "original", FontWeight: 400, TextAlign: "start", Density: "normal",
		LetterSpacing: 0.015, TextIndent: 0, CornerRadius: 4,
		TextColor: "#455b43", Background: "#f1f2e7", Border: "#dce3d0",
	}
}

type ChatWindowSettings struct {
	Enabled bool   `json:"enabled"`
	Width   int    `json:"width"`
	Height  int    `json:"height"`
	Side    string `json:"side"`
}

func DefaultChatWindowSettings() ChatWindowSettings {
	return ChatWindowSettings{Enabled: true, Width: 420, Height: 600, Side: "right"}
}

var displayChoices = map[string][]string{
	"fontPreset": {"reading", "serif", "system", "yahei", "songti", "kaiti", "humanist", "mono"},
	"style":      {"card", "rule", "plain", "outline", "strip"},
	"theme":      {"auto", "paper", "light", "dark"},
	"layout":     {"below", "inset", "reading"},
	"palette":    {"original", "sage", "sand", "sky", "lavender", "rose", "gray", "custom"},
	"textAlign":  {"start", "justify", "center"},
	"density":    {"compact", "normal", "airy"},
}

var fontWeightChoices = []int{400, 500, 600, 700}

var displayRange = map[string][2]float64{
	"fontScale": {0.5, 2}, "lineHeight": {1.4, 2.4}, "letterSpacing": {0, 0.12},
	"textIndent": {0, 3}, "cornerRadius": {0, 24},
}

func validHex(value string) bool {
	if len(value) != 7 || value[0] != '#' {
		return false
	}
	for _, r := range value[1:] {
		switch {
		case r >= '0' && r <= '9':
		case r >= 'a' && r <= 'f':
		case r >= 'A' && r <= 'F':
		default:
			return false
		}
	}
	return true
}

func boundedValue(value, fallback, min, max float64) float64 {
	if math.IsNaN(value) || math.IsInf(value, 0) {
		return fallback
	}
	return math.Max(min, math.Min(max, value))
}

// Normalized 把可能被旧版本或手工编辑污染的值收敛到插件支持的范围内。
func (d DisplaySettings) Normalized() DisplaySettings {
	defaults := DefaultDisplaySettings()
	for key, choices := range displayChoices {
		current := d.stringField(key)
		known := false
		for _, option := range choices {
			if option == current {
				known = true
			}
		}
		if !known {
			d.setStringField(key, defaults.stringField(key))
		}
	}
	knownWeight := false
	for _, weight := range fontWeightChoices {
		if d.FontWeight == weight {
			knownWeight = true
		}
	}
	if !knownWeight {
		d.FontWeight = defaults.FontWeight
	}
	for key, limit := range displayRange {
		d.setNumberField(key, boundedValue(d.numberField(key), defaults.numberField(key), limit[0], limit[1]))
	}
	for key, fallback := range map[string]string{"textColor": defaults.TextColor, "backgroundColor": defaults.Background, "borderColor": defaults.Border} {
		value := d.colorField(key)
		if !validHex(value) {
			d.setColorField(key, fallback)
		} else {
			d.setColorField(key, strings.ToLower(value))
		}
	}
	return d
}

// MergeDisplay 用插件的 patch 语义更新显示配置：只覆盖出现的字段。
func (d DisplaySettings) MergeDisplay(raw map[string]any) DisplaySettings {
	next := d
	for key, value := range raw {
		switch key {
		case "fontPreset", "style", "theme", "layout", "palette", "textAlign", "density":
			if text, ok := value.(string); ok {
				next.setStringField(key, text)
			}
		case "fontWeight":
			if weight, ok := patchNumber(value); ok {
				next.FontWeight = int(weight)
			}
		case "textColor", "backgroundColor", "borderColor":
			if text, ok := value.(string); ok {
				next.setColorField(key, text)
			}
		default:
			if number, ok := patchNumber(value); ok {
				next.setNumberField(key, number)
			}
		}
	}
	return next.Normalized()
}

// MergeChatWindow 收敛提问窗口尺寸，与小屏幕自动限制的行为一致。
func (c ChatWindowSettings) MergeChatWindow(raw map[string]any) ChatWindowSettings {
	next := c
	defaults := DefaultChatWindowSettings()
	if enabled, ok := raw["enabled"].(bool); ok {
		next.Enabled = enabled
	}
	if side, ok := raw["side"].(string); ok && (side == "left" || side == "right") {
		next.Side = side
	}
	if width, ok := patchNumber(raw["width"]); ok {
		next.Width = int(boundedValue(width, float64(defaults.Width), 320, 900))
	}
	if height, ok := patchNumber(raw["height"]); ok {
		next.Height = int(boundedValue(height, float64(defaults.Height), 360, 1000))
	}
	if next.Side == "" {
		next.Side = defaults.Side
	}
	return next
}

func (c ChatWindowSettings) Normalized() ChatWindowSettings {
	return c.MergeChatWindow(map[string]any{})
}

func patchNumber(value any) (float64, bool) {
	switch number := value.(type) {
	case float64:
		return number, true
	case int:
		return float64(number), true
	case int64:
		return float64(number), true
	}
	return 0, false
}

func (d DisplaySettings) stringField(key string) string {
	switch key {
	case "fontPreset":
		return d.FontPreset
	case "style":
		return d.Style
	case "theme":
		return d.Theme
	case "layout":
		return d.Layout
	case "palette":
		return d.Palette
	case "textAlign":
		return d.TextAlign
	case "density":
		return d.Density
	}
	return ""
}

func (d *DisplaySettings) setStringField(key, value string) {
	switch key {
	case "fontPreset":
		d.FontPreset = value
	case "style":
		d.Style = value
	case "theme":
		d.Theme = value
	case "layout":
		d.Layout = value
	case "palette":
		d.Palette = value
	case "textAlign":
		d.TextAlign = value
	case "density":
		d.Density = value
	}
}

func (d DisplaySettings) numberField(key string) float64 {
	switch key {
	case "fontScale":
		return d.FontScale
	case "lineHeight":
		return d.LineHeight
	case "letterSpacing":
		return d.LetterSpacing
	case "textIndent":
		return d.TextIndent
	case "cornerRadius":
		return d.CornerRadius
	}
	return 0
}

func (d *DisplaySettings) setNumberField(key string, value float64) {
	switch key {
	case "fontScale":
		d.FontScale = value
	case "lineHeight":
		d.LineHeight = value
	case "letterSpacing":
		d.LetterSpacing = value
	case "textIndent":
		d.TextIndent = value
	case "cornerRadius":
		d.CornerRadius = value
	}
}

func (d DisplaySettings) colorField(key string) string {
	switch key {
	case "textColor":
		return d.TextColor
	case "backgroundColor":
		return d.Background
	case "borderColor":
		return d.Border
	}
	return ""
}

func (d *DisplaySettings) setColorField(key, value string) {
	switch key {
	case "textColor":
		d.TextColor = value
	case "backgroundColor":
		d.Background = value
	case "borderColor":
		d.Border = value
	}
}
