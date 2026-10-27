/*
 * 观读轻译 1.4.1 的 content/chat.js 移植：阅读页「问」悬浮球 + 提问小窗。
 * 界面结构、悬浮球/停靠、用选文/附带本文/清除、快捷问题、停止、连续追问与回答复制保持与插件一致；
 * 只有 chrome.runtime 通信换成 Lumen 的绑定（guandu-ask / guandu-cancel），窗口配置来自 state.settings.chatWindow。
 */
(() => {
  'use strict';
  if (globalThis.GuanduChat) return;
  let instance = null;
  const api = window.lumen;
  const h = (tag, props = {}, ...children) => {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
      if (key.startsWith('on')) el.addEventListener(key.slice(2).toLowerCase(), value);
      else if (['hidden', 'disabled', 'value'].includes(key)) el[key] = value;
      else el.setAttribute(key, value);
    }
    for (const child of children.flat()) if (child !== null && child !== undefined) el.append(typeof child === 'string' ? document.createTextNode(child) : child);
    return el;
  };

  function mount() {
    if (instance) return instance;
    const scope = h('div', { class: 'gd-chat-scope', 'data-guandu-owned': 'ui' });
    document.body.append(scope);

    let config = { enabled: true, width: 420, height: 600, side: 'right' };
    let opened = false, attachment = null, lastSelection = '', history = [], busy = false, session = null, generation = 0, pending = null;

    const status = h('div', { class: 'gd-chat-local-status', role: 'status', hidden: true });
    function note(text) { status.textContent = text; status.hidden = !text; }

    const ball = h('button', { class: 'gd-chat-ball', type: 'button', title: '问读 · 共用翻译 API', 'aria-label': '打开阅读提问', 'aria-expanded': 'false', onclick: () => open() }, '问');
    const contextLabel = h('span', {}, '不附带网页内容'), contextText = h('p', { hidden: true });
    const clearContext = h('button', { class: 'button button--quiet', type: 'button', hidden: true, onclick: () => { attachment = null; renderContext(); } }, '清除');
    const log = h('div', { class: 'gd-chat-log', role: 'log', 'aria-live': 'polite' });
    function welcome() { log.replaceChildren(h('div', { class: 'gd-chat-welcome' }, h('strong', {}, '把好奇心留在书页旁'), h('span', {}, '选中一句话，再问问它的语法、词义或观点。'))); }
    const flow = h('div', { class: 'gd-chat-flow' },
      h('div', { class: 'gd-chat-context' }, h('div', { class: 'gd-chat-context-head' }, contextLabel,
        h('button', { class: 'button button--quiet', type: 'button', onclick: () => attachSelection() }, '用选文'),
        h('button', { class: 'button button--quiet', type: 'button', onclick: () => attachArticle() }, '附带本文'), clearContext), contextText),
      h('div', { class: 'gd-chat-prompts' }, ...[
        ['解释选文', '请解释选文的含义，结合语境给出自然的中文释义。'], ['拆解长句', '请拆解选文中的长难句，说明语法结构和关键表达。'], ['梳理论点', '请基于已附资料概括主旨、关键论点及其证据。']
      ].map(([label, question]) => h('button', { class: 'button button--small', type: 'button', onclick: () => { input.value = question; input.focus(); } }, label))), log, status);
    const input = h('textarea', { class: 'gd-chat-input', rows: '2', maxlength: '4000', placeholder: '例如：这句话为什么使用 would？', 'aria-label': '阅读提问内容' });
    const send = h('button', { class: 'button button--primary', type: 'submit', 'aria-label': '发送阅读问题' }, '发送 ↑');
    const stop = h('button', { class: 'button button--quiet', type: 'button', hidden: true, onclick: () => cancel() }, '停止');
    const panel = h('section', { class: 'gd-chat-panel', role: 'dialog', 'aria-label': '阅读提问', hidden: true },
      h('header', { class: 'gd-chat-header' }, h('div', { class: 'gd-chat-logo', 'aria-hidden': 'true' }, '✧'), h('div', { class: 'gd-chat-heading' }, h('h2', {}, '观读轻译 · 阅读提问'), h('p', {}, '沿用翻译接口、模型与密钥')),
        h('button', { class: 'button button--icon', type: 'button', title: '新对话', 'aria-label': '清空阅读对话', onclick: () => { if (busy) { note('请先停止当前回答。'); return; } history = []; welcome(); note(''); } }, '↺'),
        h('button', { class: 'button button--icon', type: 'button', title: '窗口大小设置', 'aria-label': '提问窗口设置', onclick: () => { globalThis.GuanduOpenSettings?.('chat'); } }, '⚙'),
        h('button', { class: 'button button--icon', type: 'button', title: '收起为悬浮球', 'aria-label': '收起阅读提问', onclick: () => close() }, '−')),
      flow, h('form', { class: 'gd-chat-composer', onsubmit: event => { event.preventDefault(); ask(); } }, h('div', { class: 'gd-chat-composer-box' }, input, h('div', { class: 'gd-chat-composer-row' }, h('span', {}, 'Ctrl / ⌘ + Enter'), stop, send))),
      h('div', { class: 'gd-chat-note' }, '发送时提交问题和所附语境，使用当前翻译服务，可能产生 API 用量。本页对话不写入备份。'));
    scope.append(ball, panel);
    welcome();

    let visible = false;
    function fit() {
      panel.dataset.side = config.side; ball.dataset.side = config.side;
      // 小屏自动限制尺寸，与插件一致。
      panel.style.width = Math.min(config.width, Math.max(320, window.innerWidth - 24)) + 'px';
      panel.style.height = Math.min(config.height, Math.max(360, window.innerHeight - 24)) + 'px';
      ball.hidden = opened || !config.enabled || !visible;
    }
    function clean(text) { return String(text || '').replace(/\s+/g, ' ').trim(); }
    function currentSelection() {
      const selection = window.getSelection(), node = selection?.anchorNode;
      const element = node?.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement;
      if (element?.closest('input,textarea,select,[contenteditable]:not([contenteditable="false"]),[data-guandu-owned="ui"]')) return '';
      return selection?.toString().trim() || '';
    }
    // 适配层：文章标题与网址来自 Lumen 当前打开的文章，而不是扩展所在页面的 location。
    function articleMeta() {
      const reader = globalThis.GuanduReader?.() || {};
      return {
        title: String(reader.title || document.title || '').slice(0, 500),
        url: String(reader.url || '').slice(0, 2000)
      };
    }
    function sourceContext(text, kind) {
      const meta = articleMeta();
      const limited = text.slice(0, 18000);
      return { text: limited, title: meta.title, url: meta.url, kind, truncated: text.length > 18000 };
    }
    function renderContext() {
      contextLabel.textContent = attachment ? `${attachment.kind === 'selection' ? '选文' : '本文节选'} · ${attachment.text.length} 字符${attachment.truncated ? ' · 部分内容' : ''}` : '不附带网页内容';
      contextText.textContent = attachment?.text || ''; contextText.hidden = !attachment; clearContext.hidden = !attachment;
    }
    function attachSelection() { const text = currentSelection() || lastSelection; if (!text) { note('请先在原文中选中需要理解的词句。'); return; } attachment = sourceContext(text, 'selection'); renderContext(); note(''); }
    function attachArticle() {
      const article = document.querySelector('#reader-viewport article,#reader-viewport [itemprop="articleBody"],#reader-viewport main,#reader-viewport [role="main"]');
      if (!article) { note('当前还没有可读的正文，请选中原文后使用“用选文”。'); return; }
      const clone = article.cloneNode(true);
      clone.querySelectorAll('[data-guandu-owned],script,style,noscript,nav,aside,footer,form,button,input,select,textarea').forEach(el => el.remove());
      const text = clean(clone.textContent);
      if (!text) { note('正文尚未加载完成，请选中已显示的原文。'); return; }
      attachment = sourceContext(text, 'article'); renderContext();
      note(attachment.truncated ? '本文较长，仅附带当前已加载文章的前 18,000 字符。' : '');
    }
    function open() { opened = true; panel.hidden = false; ball.setAttribute('aria-expanded', 'true'); const text = currentSelection(); if (text) { lastSelection = text; attachment = sourceContext(text, 'selection'); renderContext(); } fit(); input.focus({ preventScroll: true }); }
    function close() { opened = false; panel.hidden = true; ball.setAttribute('aria-expanded', 'false'); fit(); if (config.enabled && visible) ball.focus({ preventScroll: true }); }
    function setBusy(value) { busy = value; send.disabled = value; stop.hidden = !value; }
    function add(role, text) {
      log.querySelector('.gd-chat-welcome')?.remove();
      const body = h('div', { class: 'gd-chat-message-text' }, text);
      const row = h('div', { class: 'gd-chat-message', 'data-role': role }, h('div', { class: 'gd-chat-message-label' }, role === 'user' ? '你' : '阅读助手'), body);
      log.append(row); flow.scrollTop = flow.scrollHeight;
      return { row, body };
    }
    function cancel() {
      if (!busy) return;
      const old = session; generation++; session = null; setBusy(false);
      if (pending) { pending.body.textContent = '已停止本次回答。'; pending.row.dataset.error = 'true'; }
      pending = null;
      if (old) api.call('guandu-cancel', old).catch(() => {});
    }
    // wails.localhost 不是安全上下文，crypto.randomUUID 可能不可用，退回本地生成的会话号。
    function newSession() {
      const id = (globalThis.crypto && crypto.randomUUID) ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
      return 'chat-' + id;
    }
    async function copyText(text) {
      if (navigator.clipboard && window.isSecureContext) { await navigator.clipboard.writeText(text); return; }
      const runtime = window.runtime;
      if (runtime && typeof runtime.ClipboardWriteText === 'function') { await runtime.ClipboardWriteText(text); return; }
      throw new Error('clipboard unavailable');
    }
    async function ask() {
      const question = input.value.trim();
      if (busy || !question) return;
      if (question.length > 4000) { note('问题不能超过 4000 字符。'); return; }
      note(''); add('user', question); input.value = '';
      const answer = add('assistant', '正在思考…'); pending = answer;
      session = newSession(); const run = ++generation; setBusy(true);
      try {
        const result = await api.call('guandu-ask', session, { question, context: attachment ? { ...attachment } : null, history: history.map(item => ({ ...item })) });
        if (run !== generation) return;
        globalThis.GuanduChatFormat.render(answer.body, result.answer);
        const copy = h('button', { class: 'button button--quiet gd-chat-copy', type: 'button', onclick: async () => { try { await copyText(result.answer); copy.textContent = '已复制'; } catch { note('复制失败，请手动选择回答文字复制。'); } } }, '复制回答');
        answer.row.append(copy);
        history.push({ role: 'user', content: question }, { role: 'assistant', content: String(result.answer).slice(0, 12000) });
        history = history.slice(-8);
        while (history.reduce((n, item) => n + item.content.length, 0) > 48000) history.splice(0, 2);
      } catch (error) {
        if (run !== generation) return;
        answer.row.dataset.error = 'true';
        answer.body.textContent = error.message.replace(/^Error invoking remote method '[^']+': Error: /, '');
      } finally {
        if (run === generation) { session = null; pending = null; setBusy(false); flow.scrollTop = flow.scrollHeight; }
      }
    }
    input.addEventListener('keydown', event => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); ask(); } });
    panel.addEventListener('keydown', event => { if (event.key === 'Escape') { event.preventDefault(); close(); } });
    document.addEventListener('mouseup', event => {
      if (event.composedPath().includes(scope) || event.button !== 0) return;
      setTimeout(() => { const text = currentSelection(); if (text) { lastSelection = text; if (opened) { attachment = sourceContext(text, 'selection'); renderContext(); } } }, 0);
    }, true);
    window.addEventListener('pagehide', cancel); window.addEventListener('resize', fit, { passive: true });

    instance = {
      applySettings(settings) {
        if (settings?.chatWindow) config = { ...config, ...settings.chatWindow };
        if (settings?.display) globalThis.GuanduAppearance?.applyLayer(scope, settings.display);
        fit();
      },
      setVisible(value) { visible = value; panel.hidden = !value || !opened; ball.hidden = !value || opened || !config.enabled; },
      open, close, fit,
      get opened() { return opened; }
    };
    fit();
    return instance;
  }

  function chat() { return mount(); }
  Object.defineProperty(globalThis, 'GuanduChat', { value: Object.freeze({
    mount,
    applySettings: settings => instance?.applySettings(settings),
    setVisible: value => instance?.setVisible(value),
    open: () => mount().open(),
    close: () => instance?.close(),
    isOpen: () => Boolean(instance?.opened)
  }) });
})();
