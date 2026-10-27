package main

import (
	"fmt"
	"github.com/wailsapp/wails/v2/pkg/runtime"
	"golang.org/x/sys/windows"
		"os"
	"os/exec"
	"strconv"
	"time"
)

// A fresh process recreates every WebView and reloads Windows proxy settings.
// The replacement waits for this process to exit before opening the same profiles.
func (a *App) Restart() (bool, error) {
	a.restartMu.Lock()
	defer a.restartMu.Unlock()
	if a.restarting {
		return true, nil
	}
	executable, err := os.Executable()
	if err != nil {
		return false, err
	}
	cmd := exec.Command(executable, "--lumen-restart-wait", strconv.Itoa(os.Getpid()))
	if err = cmd.Start(); err != nil {
		return false, fmt.Errorf("无法重新启动应用: %w", err)
	}
	a.restarting = true
	go func() { time.Sleep(300 * time.Millisecond); runtime.Quit(a.ctx) }()
	return true, nil
}

func waitForRestart() {
	if len(os.Args) != 3 || os.Args[1] != "--lumen-restart-wait" {
		return
	}
	pid, err := strconv.ParseUint(os.Args[2], 10, 32)
	if err != nil || pid == 0 {
		os.Exit(1)
	}
	process, err := windows.OpenProcess(windows.SYNCHRONIZE, false, uint32(pid))
	if err != nil {
		return
	}
	result, err := windows.WaitForSingleObject(process, 25000)
	windows.CloseHandle(process)
	if err != nil || result != windows.WAIT_OBJECT_0 {
		os.Exit(1)
	}
}
