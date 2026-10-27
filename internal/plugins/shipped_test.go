package plugins

import (
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"testing"
)

// 随仓库发布的三个插件 ZIP 是用户第一次使用时唯一能拿到的官方包，
// 打包脚本只检查 id/version，格式错误要到用户导入时才暴露，所以这里提前跑一遍完整校验。
func TestShippedPluginsValidate(t *testing.T) {
	root := filepath.Join("..", "..", "plugins")
	entries, err := os.ReadDir(root)
	if err != nil {
		t.Fatalf("read %s: %v", root, err)
	}
	versionPattern := regexp.MustCompile(`^\d+\.\d+\.\d+$`)
	seenSource := map[string]string{}
	count := 0
	for _, entry := range entries {
		if !entry.IsDir() || entry.Name() == "runtime" {
			continue
		}
		count++
		path := filepath.Join(root, entry.Name(), "plugin.json")
		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("%s: %v", entry.Name(), err)
		}
		var plugin Plugin
		if err := json.Unmarshal(data, &plugin); err != nil {
			t.Fatalf("%s: parse: %v", entry.Name(), err)
		}
		if err := validatePlugin(plugin); err != nil {
			t.Fatalf("%s: %v", entry.Name(), err)
		}
		if plugin.ID != entry.Name() {
			t.Fatalf("%s: 插件 ID %q 与目录名不一致", entry.Name(), plugin.ID)
		}
		if !versionPattern.MatchString(plugin.Version) {
			t.Fatalf("%s: 版本号 %q 不是 X.Y.Z", entry.Name(), plugin.Version)
		}
		for _, source := range plugin.Sources {
			if source.ID == "" || source.Name == "" {
				t.Fatalf("%s: 来源 %q 缺少 id 或 name", entry.Name(), source.ID)
			}
			if source.Feed == "" && source.URL == "" {
				t.Fatalf("%s: 来源 %s 既没有 feed 也没有 url", entry.Name(), source.ID)
			}
			if owner, dup := seenSource[source.ID]; dup {
				t.Fatalf("来源 ID %s 同时属于 %s 和 %s", source.ID, owner, entry.Name())
			}
			seenSource[source.ID] = entry.Name()
		}
	}
	if count != 3 {
		t.Fatalf("仓库插件数量 = %d，期望 3", count)
	}
}
