package main

import (
	"embed"
	"io/fs"
	"os"
	"path/filepath"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
	windowsopts "github.com/wailsapp/wails/v2/pkg/options/windows"
)

//go:embed all:ui
var uiAssets embed.FS

func main() {
	app := NewApp()

	ui, err := fs.Sub(uiAssets, "ui")
	if err != nil {
		panic(err)
	}

	webviewData := filepath.Join(os.Getenv("APPDATA"), "Lumen", "webview2", "ui")

	err = wails.Run(&options.App{
		Title:            "Lumen · 观读",
		Width:            1440,
		Height:           940,
		MinWidth:         1120,
		MinHeight:        740,
		AssetServer:      &assetserver.Options{Assets: ui},
		BackgroundColour: &options.RGBA{R: 246, G: 248, B: 245, A: 1},
		OnStartup:        app.startup,
		OnDomReady:       app.domReady,
		OnBeforeClose:    app.beforeClose,
		Bind:             []interface{}{app},
		Windows: &windowsopts.Options{
			WebviewUserDataPath: webviewData,
			DisableWindowIcon:   false,
		},
	})
	if err != nil {
		println("Error:", err.Error())
	}
}
