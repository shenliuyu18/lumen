//go:build windows

package secrets

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

var (
	crypt32       = windows.NewLazySystemDLL("crypt32.dll")
	kernel32      = windows.NewLazySystemDLL("kernel32.dll")
	procProtect   = crypt32.NewProc("CryptProtectData")
	procUnprotect = crypt32.NewProc("CryptUnprotectData")
	procLocalFree = kernel32.NewProc("LocalFree")
)

type dataBlob struct {
	cbData uint32
	pbData *byte
}

type Manager struct {
	mu   sync.Mutex
	path string
	keys map[string]string
}

func New(path string) *Manager {
	manager := &Manager{path: path}
	manager.keys = manager.read()
	return manager
}

func (m *Manager) Status() map[string]bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return map[string]bool{
		"microsoft": m.keys["microsoft"] != "",
		"deepl":     m.keys["deepl"] != "",
		"ai":        m.keys["ai"] != "",
	}
}

func (m *Manager) Get(provider string) string {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.keys[provider]
}

func (m *Manager) Set(provider, key string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	next := make(map[string]string, len(m.keys)+1)
	for name, value := range m.keys {
		next[name] = value
	}
	next[provider] = key
	if err := m.write(next); err != nil {
		return err
	}
	m.keys = next
	return nil
}

func (m *Manager) Remove(provider string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	next := make(map[string]string, len(m.keys))
	for name, value := range m.keys {
		if name != provider {
			next[name] = value
		}
	}
	if err := m.write(next); err != nil {
		return err
	}
	m.keys = next
	return nil
}

func (m *Manager) read() map[string]string {
	data, err := os.ReadFile(m.path)
	if err != nil {
		return map[string]string{}
	}
	plain, err := unprotect(data)
	if err != nil {
		return map[string]string{}
	}
	var keys map[string]string
	if err := json.Unmarshal(plain, &keys); err != nil || keys == nil {
		return map[string]string{}
	}
	return keys
}

func (m *Manager) write(keys map[string]string) error {
	plain, err := json.Marshal(keys)
	if err != nil {
		return err
	}
	encrypted, err := protect(plain)
	if err != nil {
		return fmt.Errorf("无法使用系统加密保存密钥: %w", err)
	}
	if err := os.MkdirAll(filepath.Dir(m.path), 0o755); err != nil {
		return err
	}
	tmp := m.path + ".tmp"
	if err := os.WriteFile(tmp, encrypted, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, m.path)
}

func protect(data []byte) ([]byte, error) {
	var input, output dataBlob
	if len(data) > 0 {
		input.cbData = uint32(len(data))
		input.pbData = &data[0]
	}
	result, _, callErr := procProtect.Call(
		uintptr(unsafe.Pointer(&input)),
		0,
		0,
		0,
		0,
		0,
		uintptr(unsafe.Pointer(&output)),
	)
	if result == 0 {
		return nil, syscallError(callErr)
	}
	defer procLocalFree.Call(uintptr(unsafe.Pointer(output.pbData)))
	return append([]byte(nil), unsafe.Slice(output.pbData, output.cbData)...), nil
}

func unprotect(data []byte) ([]byte, error) {
	var input, output dataBlob
	input.cbData = uint32(len(data))
	input.pbData = &data[0]
	result, _, callErr := procUnprotect.Call(
		uintptr(unsafe.Pointer(&input)),
		0,
		0,
		0,
		0,
		0,
		uintptr(unsafe.Pointer(&output)),
	)
	if result == 0 {
		return nil, syscallError(callErr)
	}
	defer procLocalFree.Call(uintptr(unsafe.Pointer(output.pbData)))
	return append([]byte(nil), unsafe.Slice(output.pbData, output.cbData)...), nil
}

func syscallError(err error) error {
	if errno, ok := err.(syscall.Errno); ok && errno != windows.ERROR_SUCCESS {
		return errno
	}
	return windows.ERROR_INVALID_DATA
}
