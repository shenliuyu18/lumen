//go:build windows

package webhost

import (
	"syscall"
	"unsafe"

	"golang.org/x/sys/windows"
)

const (
	wsChild        = 0x40000000
	wsVisible      = 0x10000000
	wsClipChildren = 0x02000000
	wsClipSiblings = 0x04000000
	wsOverlapped   = 0x00CF0000
	wsExToolWindow = 0x00000080
	swHide         = 0
	swShow         = 5
	hwndTop        = 0
	swpNoActivate  = 0x0010
	swpShowWindow  = 0x0040
	wmApp          = 0x8000
	wmClose        = 0x0010
	wmDestroy      = 0x0002
	wmSize         = 0x0005
)

var gwlpOwner = ^uintptr(7)

var (
	user32                 = windows.NewLazySystemDLL("user32.dll")
	procEnumWindows        = user32.NewProc("EnumWindows")
	procGetWindowThreadPID = user32.NewProc("GetWindowThreadProcessId")
	procGetClassNameW      = user32.NewProc("GetClassNameW")
	procGetWindowTextW     = user32.NewProc("GetWindowTextW")
	procIsWindowVisible    = user32.NewProc("IsWindowVisible")
	procGetClientRect      = user32.NewProc("GetClientRect")
	procGetDpiForWindow    = user32.NewProc("GetDpiForWindow")
	procCreateWindowExW    = user32.NewProc("CreateWindowExW")
	procDestroyWindow      = user32.NewProc("DestroyWindow")
	procSetWindowPos       = user32.NewProc("SetWindowPos")
	procShowWindow         = user32.NewProc("ShowWindow")
	procPostMessageW       = user32.NewProc("PostMessageW")
	procGetMessageW        = user32.NewProc("GetMessageW")
	procTranslateMessage   = user32.NewProc("TranslateMessage")
	procDispatchMessageW   = user32.NewProc("DispatchMessageW")
	procSetWindowLongPtrW  = user32.NewProc("SetWindowLongPtrW")
)

type winRect struct {
	Left   int32
	Top    int32
	Right  int32
	Bottom int32
}

type winMsg struct {
	Hwnd     uintptr
	Message  uint32
	_        uint32
	WParam   uintptr
	LParam   uintptr
	Time     uint32
	Pt       struct{ X, Y int32 }
	LPrivate uint32
}

func findWailsWindow() uintptr {
	pid := uint32(windows.GetCurrentProcessId())
	var found uintptr
	cb := syscall.NewCallback(func(hwnd uintptr, _ uintptr) uintptr {
		var owner uint32
		procGetWindowThreadPID.Call(hwnd, uintptr(unsafe.Pointer(&owner)))
		if owner != pid {
			return 1
		}
		visible, _, _ := procIsWindowVisible.Call(hwnd)
		if visible == 0 {
			return 1
		}
		var class [256]uint16
		n, _, _ := procGetClassNameW.Call(hwnd, uintptr(unsafe.Pointer(&class[0])), uintptr(len(class)))
		if n == 0 {
			return 1
		}
		if windows.UTF16ToString(class[:n]) == "wailsWindow" {
			found = hwnd
			return 0
		}
		return 1
	})
	procEnumWindows.Call(cb, 0)
	return found
}

func clientRect(hwnd uintptr) (winRect, bool) {
	var rect winRect
	ok, _, _ := procGetClientRect.Call(hwnd, uintptr(unsafe.Pointer(&rect)))
	return rect, ok != 0
}

func dpiForWindow(hwnd uintptr) float64 {
	dpi, _, _ := procGetDpiForWindow.Call(hwnd)
	if dpi == 0 {
		return 96
	}
	return float64(dpi)
}

func createChild(parent uintptr, x, y, w, h int32) uintptr {
	class, _ := syscall.UTF16PtrFromString("STATIC")
	style := uintptr(wsChild | wsClipChildren | wsClipSiblings)
	child, _, _ := procCreateWindowExW.Call(
		0,
		uintptr(unsafe.Pointer(class)),
		0,
		style,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		parent,
		0,
		0,
		0,
	)
	return child
}

func createTopLevel(owner uintptr, title string, x, y, w, h int32) uintptr {
	class, _ := syscall.UTF16PtrFromString("STATIC")
	windowTitle, _ := syscall.UTF16PtrFromString(title)
	style := uintptr(wsOverlapped | wsClipChildren)
	window, _, _ := procCreateWindowExW.Call(
		wsExToolWindow,
		uintptr(unsafe.Pointer(class)),
		uintptr(unsafe.Pointer(windowTitle)),
		style,
		uintptr(x), uintptr(y), uintptr(w), uintptr(h),
		0,
		0,
		0,
		0,
	)
	if window != 0 && owner != 0 {
		procSetWindowLongPtrW.Call(window, gwlpOwner, owner)
	}
	return window
}

func moveWindow(hwnd uintptr, x, y, w, h int32, show bool) {
	flags := uintptr(swpNoActivate)
	if show {
		flags |= swpShowWindow
	}
	procSetWindowPos.Call(hwnd, uintptr(hwndTop), uintptr(x), uintptr(y), uintptr(w), uintptr(h), flags)
}

func showWindow(hwnd uintptr, show bool) {
	cmd := uintptr(swHide)
	if show {
		cmd = swShow
	}
	procShowWindow.Call(hwnd, cmd)
}

func postWake(hwnd uintptr) {
	procPostMessageW.Call(hwnd, uintptr(wmApp+1), 0, 0)
}

func messageLoop(hwnd uintptr, drain func(), onSize func(), onClose func()) {
	var msg winMsg
	for {
		r, _, _ := procGetMessageW.Call(uintptr(unsafe.Pointer(&msg)), 0, 0, 0)
		if int32(r) <= 0 {
			return
		}
		if msg.Hwnd == hwnd && msg.Message == wmApp+1 {
			drain()
			continue
		}
		if msg.Hwnd == hwnd && msg.Message == wmClose {
			if onClose != nil {
				onClose()
			}
			continue
		}
		procTranslateMessage.Call(uintptr(unsafe.Pointer(&msg)))
		procDispatchMessageW.Call(uintptr(unsafe.Pointer(&msg)))
		if msg.Hwnd == hwnd && msg.Message == wmSize && onSize != nil {
			onSize()
		}
	}
}
