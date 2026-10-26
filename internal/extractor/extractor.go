package extractor

import (
	"embed"
	"fmt"
)

//go:embed readability.js extract.js
var files embed.FS

func Script() (string, error) {
	readability, err := files.ReadFile("readability.js")
	if err != nil {
		return "", err
	}
	extract, err := files.ReadFile("extract.js")
	if err != nil {
		return "", err
	}
	return string(readability) + "\n;" + string(extract), nil
}

func MustScript() string {
	script, err := Script()
	if err != nil {
		panic(fmt.Sprintf("extractor assets: %v", err))
	}
	return script
}
