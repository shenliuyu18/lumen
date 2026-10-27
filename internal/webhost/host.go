//go:build windows

package webhost

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/wailsapp/go-webview2/pkg/edge"
	"golang.org/x/sys/windows"
)

type command struct {
	kind   string
	script string
	url    string
	x, y   int32
	w, h   int32
	show   bool
}

type Options struct {
	DataPath        string
	BlockFunc       func(url string) bool
	ExtractScript   string
	OnMessage       func(string)
	EnableSelection bool
	TopLevel        bool
	Title           string
	Width           int32
	Height          int32
}

type evalOutcome struct {
	value string
	err   string
}

type Host struct {
	main      uintptr
	child     uintptr
	chrome    *edge.Chromium
	blockFunc func(string) bool
	cmds      chan command
	onMessage func(string)
	ready     chan error
	resultCh  chan string
	extractJS string
	dataPath  string
	selection bool
	topLevel  bool
	title     string
	width     int32
	height    int32
	zoom      float64
	evalSeq   uint64
	evalMu    sync.Mutex
	evals     map[string]chan evalOutcome
	closeOnce sync.Once
}

func New(options Options) *Host {
	return &Host{
		cmds:      make(chan command, 32),
		onMessage: options.OnMessage,
		ready:     make(chan error, 1),
		resultCh:  make(chan string, 1),
		extractJS: options.ExtractScript,
		blockFunc: options.BlockFunc,
		dataPath:  options.DataPath,
		selection: options.EnableSelection,
		topLevel:  options.TopLevel,
		title:     options.Title,
		width:     options.Width,
		height:    options.Height,
		zoom:      1,
		evals:     map[string]chan evalOutcome{},
	}
}

func (h *Host) Start(timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for {
		h.main = findWailsWindow()
		if h.main != 0 {
			break
		}
		if time.Now().After(deadline) {
			return errors.New("未找到 Wails 主窗口")
		}
		time.Sleep(100 * time.Millisecond)
	}
	go h.run()
	select {
	case err := <-h.ready:
		return err
	case <-time.After(timeout):
		return errors.New("WebView2 初始化超时")
	}
}

func (h *Host) run() {
	runtime.LockOSThread()
	if err := windows.CoInitializeEx(0, windows.COINIT_APARTMENTTHREADED); err != nil {
		fmt.Println("[webhost] CoInitializeEx:", err)
	}
	defer windows.CoUninitialize()

	rect, ok := clientRect(h.main)
	if !ok {
		h.ready <- errors.New("读取主窗口尺寸失败")
		return
	}
	width := rect.Right - rect.Left
	height := rect.Bottom - rect.Top
	x, y := int32(40), int32(90)
	w, hgt := width-80, height-180
	if w < 320 || hgt < 240 {
		x, y, w, hgt = 20, 20, width-40, height-40
	}

	if h.topLevel {
		w, hgt = h.width, h.height
		if w <= 0 {
			w = 1100
		}
		if hgt <= 0 {
			hgt = 780
		}
		x, y = 260, 100
		title := h.title
		if title == "" {
			title = "Lumen"
		}
		h.child = createTopLevel(h.main, title, x, y, w, hgt)
	} else {
		h.child = createChild(h.main, x, y, w, hgt)
	}
	if h.child == 0 {
		h.ready <- errors.New("创建子窗口失败")
		return
	}
	if !h.topLevel {
		moveWindow(h.child, x, y, w, hgt, false)
	}

	chromium := edge.NewChromium()
	chromium.AdditionalBrowserArgs = append(chromium.AdditionalBrowserArgs,
		"--disable-component-update",
		"--disable-gpu-shader-disk-cache",
		"--disk-cache-size=33554432",
		"--media-cache-size=8388608",
	)
	for _, name := range []string{"HTTPS_PROXY", "HTTP_PROXY"} {
		if proxy := os.Getenv(name); proxy != "" {
			chromium.AdditionalBrowserArgs = append(chromium.AdditionalBrowserArgs, "--proxy-server="+proxy)
			break
		}
	}
	dataPath := h.dataPath
	if dataPath == "" {
		dataPath = filepath.Join(os.Getenv("APPDATA"), "Lumen", "webview2", "reader")
	}
	chromium.DataPath = dataPath
	if h.blockFunc != nil {
		// 域名级广告屏蔽：命中规则的请求直接回 403 空响应，页面根本拿不到广告资源。
		block := h.blockFunc
		chromium.WebResourceRequestedCallback = func(request *edge.ICoreWebView2WebResourceRequest, args *edge.ICoreWebView2WebResourceRequestedEventArgs) {
			target, err := request.GetUri()
			if err != nil || !block(target) {
				return
			}
			fmt.Println("[adblock] blocked", target)
			response, err := chromium.Environment().CreateWebResourceResponse([]byte{}, 403, "Blocked", "Content-Type: text/plain")
			if err != nil {
				return
			}
			defer response.Release()
			_ = args.PutResponse(response)
		}
	}
	chromium.SetErrorCallback(func(err error) {
		fmt.Println("[webhost] error:", err)
	})
	chromium.MessageCallback = func(message string, _ *edge.ICoreWebView2, _ *edge.ICoreWebView2WebMessageReceivedEventArgs) {
		if strings.Contains(message, `"type":"extract-result"`) {
			select {
			case h.resultCh <- message:
			default:
			}
		}
		if strings.Contains(message, `"type":"eval-result"`) {
			var result struct {
				ID    string          `json:"id"`
				Value json.RawMessage `json:"value"`
				Error string          `json:"error"`
			}
			if json.Unmarshal([]byte(message), &result) == nil && result.ID != "" {
				h.evalMu.Lock()
				channel := h.evals[result.ID]
				h.evalMu.Unlock()
				if channel != nil {
					outcome := evalOutcome{value: string(result.Value), err: result.Error}
					select {
					case channel <- outcome:
					default:
					}
				}
			}
		}
		if h.onMessage != nil {
			h.onMessage(message)
		}
	}
	chromium.ProcessFailedCallback = func(_ *edge.ICoreWebView2, _ *edge.ICoreWebView2ProcessFailedEventArgs) {
		fmt.Println("[webhost] process failed")
	}

	if !chromium.Embed(h.child) {
		h.ready <- errors.New("WebView2 Embed 失败")
		return
	}
	h.chrome = chromium
	if h.blockFunc != nil {
		// 过滤器必须在 WebView 创建之后注册，否则 AddWebResourceRequestedFilter 会空指针。
		chromium.AddWebResourceRequestedFilter("*", edge.COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL)
	}
	fmt.Println("[webhost] embedded, dpi=", dpiForWindow(h.main))
	chromium.SetGlobalPermission(edge.CoreWebView2PermissionStateDeny)
	if h.selection {
		chromium.Init(selectionScript)
	}
	chromium.Init(locationScript)
	chromium.Init(navigationGuardScript)
	if h.topLevel {
		showWindow(h.child, true)
		_ = chromium.Show()
	} else {
		moveWindow(h.child, x, y, w, hgt, false)
		showWindow(h.child, false)
		_ = chromium.Hide()
	}
	h.ready <- nil

	messageLoop(h.child, h.drain, func() {
		if h.chrome != nil {
			h.chrome.Resize()
		}
	}, func() {
		showWindow(h.child, false)
		_ = chromium.Hide()
	})
	fmt.Println("[webhost] message loop exited")
}

func (h *Host) post(cmd command) {
	select {
	case h.cmds <- cmd:
	default:
		return
	}
	postWake(h.child)
}

func (h *Host) drain() {
	for {
		select {
		case cmd := <-h.cmds:
			switch cmd.kind {
			case "eval":
				h.chrome.Eval(cmd.script)
			case "navigate":
				h.chrome.Navigate(cmd.url)
			case "bounds":
				moveWindow(h.child, cmd.x, cmd.y, cmd.w, cmd.h, cmd.show)
				h.chrome.Resize()
			case "show":
				if cmd.show {
					_ = h.chrome.Show()
					showWindow(h.child, true)
					h.chrome.Focus()
				} else {
					_ = h.chrome.Hide()
					showWindow(h.child, false)
				}
			case "extract":
				h.chrome.Eval(h.extractJS)
			case "control":
				switch cmd.script {
				case "back":
					h.chrome.Eval("history.back()")
				case "forward":
					h.chrome.Eval("history.forward()")
				case "reload":
					h.chrome.Eval("location.reload()")
				case "zoom-in":
					if h.zoom < 2 {
						h.zoom += 0.1
						h.chrome.PutZoomFactor(h.zoom)
					}
				case "zoom-out":
					if h.zoom > 0.6 {
						h.zoom -= 0.1
						h.chrome.PutZoomFactor(h.zoom)
					}
				}
			}
		default:
			return
		}
	}
}

func (h *Host) SetBounds(x, y, w, hgt int32, show bool) {
	h.post(command{kind: "bounds", x: x, y: y, w: w, h: hgt, show: show})
}

func (h *Host) SetBoundsCSS(x, y, w, hgt float64) {
	scale := dpiForWindow(h.main) / 96
	h.post(command{kind: "bounds",
		x: int32(x * scale), y: int32(y * scale),
		w: int32(w * scale), h: int32(hgt * scale), show: false})
}

func (h *Host) Extract(timeout time.Duration) (string, error) {
	select {
	case <-h.resultCh:
	default:
	}
	h.post(command{kind: "extract"})
	select {
	case message := <-h.resultCh:
		return message, nil
	case <-time.After(timeout):
		return "", errors.New("提取正文超时")
	}
}

func (h *Host) Show(show bool) {
	h.post(command{kind: "show", show: show})
}

func (h *Host) Navigate(url string) {
	h.post(command{kind: "navigate", url: url})
}

func (h *Host) Control(action string) {
	h.post(command{kind: "control", script: action})
}

func (h *Host) Eval(script string) {
	if script == "" {
		return
	}
	h.post(command{kind: "eval", script: script})
}

func (h *Host) EvalResult(script string, timeout time.Duration) (string, error) {
	id := fmt.Sprintf("%d", atomic.AddUint64(&h.evalSeq, 1))
	channel := make(chan evalOutcome, 1)
	h.evalMu.Lock()
	h.evals[id] = channel
	h.evalMu.Unlock()
	wrapped := `(function(){try{var value=(function(){` + script + `})();window.chrome.webview.postMessage(JSON.stringify({v:1,type:'eval-result',id:'` + id + `',value:value}));}catch(e){window.chrome.webview.postMessage(JSON.stringify({v:1,type:'eval-result',id:'` + id + `',error:String((e&&e.message)||e)}));}})();`
	h.post(command{kind: "eval", script: wrapped})
	var outcome evalOutcome
	select {
	case outcome = <-channel:
	case <-time.After(timeout):
		h.evalMu.Lock()
		delete(h.evals, id)
		h.evalMu.Unlock()
		return "", errors.New("脚本执行超时")
	}
	h.evalMu.Lock()
	delete(h.evals, id)
	h.evalMu.Unlock()
	if outcome.err != "" {
		return "", errors.New(outcome.err)
	}
	var value string
	if json.Unmarshal([]byte(outcome.value), &value) == nil {
		return value, nil
	}
	return outcome.value, nil
}

func (h *Host) Close() {
	h.closeOnce.Do(func() {
		procPostMessageW.Call(h.child, uintptr(wmClose), 0, 0)
	})
}

const selectionScript = `(function(){
  function sel(){
    var s=window.getSelection(),t=s&&s.toString().trim();
    if(!t||t.length>1500)return;
    var n=s.anchorNode&&s.anchorNode.parentElement;
    if(n&&n.closest('input,textarea,[contenteditable="true"]'))return;
    var ctx=(n&&n.closest('p,blockquote,li')&&n.closest('p,blockquote,li').textContent||'').trim().slice(0,2000);
    window.chrome.webview.postMessage(JSON.stringify({v:1,type:'selection',text:t,context:ctx,url:location.href}));
  }
  addEventListener('mouseup',function(){setTimeout(sel,30)});
  addEventListener('keyup',function(e){if(e.key==='Shift')sel()});
})();`

const locationScript = `(function(){
  function report(){window.chrome.webview.postMessage(JSON.stringify({v:1,type:'location',url:location.href,title:document.title,back:history.length>1}));}
  addEventListener('DOMContentLoaded',report);
  addEventListener('load',report);
  addEventListener('popstate',report);
  addEventListener('hashchange',report);
})();`

const navigationGuardScript = `(function(){
  function send(url,download){try{window.chrome.webview.postMessage(JSON.stringify({v:1,type:'open-external',url:String(url||''),download:!!download}));}catch(e){}}
  window.open=function(url){if(url)send(url,false);return null;};
  addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a[target="_blank"],a[download]');if(!a)return;e.preventDefault();send(a.href,a.hasAttribute('download'));},true);
})();`
