let deepseekMode = 'web';
let deepseekDraft = '';
let deepseekAnswer = '';
let deepseekAnswerError = false;
let deepseekBusy = false;

function deepseekContext() {
  const parts = [];
  const title = bilingual?.title || readerArticle?.title || '';
  const url = bilingual?.url || readerInfo?.url || readerArticle?.url || '';
  if (title) parts.push('文章：' + title);
  if (url) parts.push('来源：' + url);
  if (selected?.text) {
    parts.push('选中内容：\n' + selected.text);
  } else if (bilingual?.paragraphs?.length) {
    const paragraph = bilingual.paragraphs.find(p => p.tag === 'p') || bilingual.paragraphs[0];
    if (paragraph?.text) parts.push('当前段落：\n' + paragraph.text);
  }
  return parts.join('\n\n');
}

function syncDeepSeekBounds() {
  if (page !== 'reader' || activeStudyTab !== 'deepseek' || deepseekMode !== 'web') return;
  const element = document.getElementById('deepseek-viewport');
  if (!element) return;
  const bounds = element.getBoundingClientRect();
  api.call('deepseek-bounds', { x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height });
}

function renderDeepSeekPanel() {
  const panel = document.getElementById('deepseek-panel');
  if (!panel) return;
  if (deepseekMode === 'web') {
    panel.innerHTML = `<div class="deepseek-panel deepseek-web">
      <div class="chat-toolbar"><button class="btn small" data-action="deepseek-context">复制选文提问</button><button class="btn small" data-action="deepseek-reload">刷新</button><button class="btn small" data-action="deepseek-popout" title="独立小窗，便于登录">小窗</button><button class="btn small" data-external="https://chat.deepseek.com/">浏览器 ↗</button><button class="btn small" data-action="deepseek-mode-api">API 问答</button></div>
      <p class="chat-note" id="deepseek-note">DeepSeek 官方网页 · 请登录自己的账户。复制选文后，在下面粘贴并输入问题；侧栏不会自动读取聊天或自动发送文章。</p>
      <div id="deepseek-viewport"><span class="note">正在连接 DeepSeek…</span></div>
    </div>`;
    syncDeepSeekBounds();
    call('deepseek-show');
    return;
  }
  const saved = Boolean(state?.keyStatus?.deepseek);
  panel.innerHTML = `<div class="deepseek-panel">
    <div class="study-tools"><button class="btn small" data-action="deepseek-mode-web">使用网页版</button></div>
    <p class="chat-note">DeepSeek API · 只有点击“提问”时才会把文章/选中内容与问题发送给 DeepSeek。</p>
    <label class="deepseek-field">API 密钥<input id="deepseek-key" type="password" autocomplete="off" placeholder="${saved ? '已保存，留空即可继续使用' : 'sk-...'}"></label>
    <div class="study-tools"><button class="btn small" data-action="deepseek-save">${saved ? '更新密钥' : '保存密钥'}</button><span class="note" id="deepseek-key-status">${saved ? '密钥已保存' : '尚未保存密钥'}</span></div>
    <label class="deepseek-field">问题<textarea id="deepseek-question" rows="4" placeholder="例如：这段论证的主旨、证据和值得学习的表达是什么？">${esc(deepseekDraft || '')}</textarea></label>
    <div class="study-tools"><button class="btn small" data-action="deepseek-ask" ${deepseekBusy ? 'disabled' : ''}>${deepseekBusy ? '提问中…' : '提问'}</button></div>
    <div id="deepseek-answer" class="deepseek-answer ${deepseekAnswerError ? 'error-text' : ''}">${esc(deepseekAnswer || '回答会显示在这里。')}</div>
  </div>`;
}

document.addEventListener('click', async e => {
  const button = e.target.closest('button');
  if (!button) return;

  if (button.dataset.action === 'deepseek-mode-web') {
    deepseekMode = 'web';
    renderDeepSeekPanel();
    return;
  }
  if (button.dataset.action === 'deepseek-mode-api') {
    deepseekMode = 'api';
    await call('deepseek-hide');
    renderDeepSeekPanel();
    return;
  }
  if (button.dataset.action === 'deepseek-reload') {
    await call('deepseek-reload');
    return;
  }
  if (button.dataset.action === 'deepseek-popout') {
    await call('deepseek-popout');
    return;
  }
  if (button.dataset.action === 'deepseek-context') {
    const text = selected?.text || bilingual?.paragraphs?.find(p => p.tag === 'p')?.text || '';
    await call('deepseek-copy-context', `我在阅读这篇英文文章：${bilingual?.title || readerArticle?.title || ''}\n来源：${bilingual?.url || readerArticle?.url || ''}\n\n${text ? `选中内容：\n${text}\n\n` : ''}请结合语境帮助我理解以下问题：\n`);
    toast('已复制选文和出处，请在 DeepSeek 输入框中粘贴后提问');
    return;
  }

  if (button.dataset.action === 'deepseek-save' || button.dataset.action === 'deepseek-ask') {
    const keyInput = document.getElementById('deepseek-key');
    const key = keyInput?.value.trim() || '';
    if (button.dataset.action === 'deepseek-save' && !key && !state?.keyStatus?.deepseek) {
      const status = document.getElementById('deepseek-key-status');
      if (status) status.textContent = '请先填写 API 密钥';
      return;
    }
    if (key) {
      button.disabled = true;
      try {
        await call('deepseek-config', key);
        if (state?.keyStatus) state.keyStatus.deepseek = true;
        const status = document.getElementById('deepseek-key-status');
        if (status) status.textContent = '密钥已保存';
        if (keyInput) keyInput.value = '';
      } catch (error) {
        const status = document.getElementById('deepseek-key-status');
        if (status) status.textContent = '保存失败：' + error.message;
        button.disabled = false;
        return;
      }
      button.disabled = false;
    }
    if (button.dataset.action === 'deepseek-save') {
      toast('DeepSeek 密钥已保存');
      return;
    }
  }

  if (button.dataset.action !== 'deepseek-ask' || deepseekBusy) return;
  if (!state?.keyStatus?.deepseek) {
    const status = document.getElementById('deepseek-key-status');
    if (status) status.textContent = '请先填写并保存 API 密钥';
    return;
  }
  const questionInput = document.getElementById('deepseek-question');
  deepseekDraft = questionInput?.value.trim() || '';
  const question = deepseekDraft || '请结合上下文解释这段内容，说明主旨、证据和值得学习的表达。';
  deepseekBusy = true;
  deepseekAnswer = '正在向 DeepSeek 提问…';
  deepseekAnswerError = false;
  renderDeepSeekPanel();
  try {
    const result = await call('deepseek-ask', question, deepseekContext());
    deepseekAnswer = result?.text || 'DeepSeek 未返回内容。';
    deepseekAnswerError = false;
  } catch (error) {
    deepseekAnswer = '提问失败：' + error.message.replace(/^Error invoking remote method '[^']+': Error: /, '');
    deepseekAnswerError = true;
  } finally {
    deepseekBusy = false;
    renderDeepSeekPanel();
  }
});

document.addEventListener('input', e => {
  if (e.target.id === 'deepseek-question') deepseekDraft = e.target.value;
});

window.addEventListener('resize', syncDeepSeekBounds);
window.lumen.on('deepseek-status', status => {
  const note = document.getElementById('deepseek-note');
  if (note && status.error) note.textContent = 'DeepSeek 网页连接失败：' + status.error + '。可尝试刷新、小窗登录或系统浏览器。';
  syncDeepSeekBounds();
});
