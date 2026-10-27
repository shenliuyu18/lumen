/*
 * 观读轻译 1.4.1 的设置界面移植：
 *   选项表来自 core/display.mjs（DISPLAY_DEFAULTS / DISPLAY_CHOICES / 四个预设），
 *   编辑器结构来自 options/display.mjs（实时预览 → 预设 → 常用项 → 加粗与字号 → 自定义颜色 → 精细排版 → 恢复默认）。
 * 适配层只把 chrome 存储换成 Lumen 的 settings 绑定：保存后只刷新预览与译文，不整页重绘。
 */

const guanduDisplayChoices = {
  fontPreset: [['reading', '舒适黑体'], ['serif', '中英书卷衬线'], ['system', '系统字体'], ['yahei', '微软雅黑 / 苹方'], ['songti', '宋体 / 思源宋体'], ['kaiti', '楷体'], ['humanist', '人文无衬线'], ['mono', '等宽字体']],
  style: [['card', '柔和底色卡片'], ['rule', '左侧细线'], ['plain', '简洁文字'], ['outline', '通透描边'], ['strip', '顶部色条']],
  theme: [['auto', '适配网页 / 系统'], ['paper', '暖纸色'], ['light', '清透白'], ['dark', '深色松林']],
  layout: [['below', '跟随原文宽度'], ['inset', '缩进对照'], ['reading', '居中窄栏']],
  palette: [['original', '经典 · 跟随主题'], ['sage', '鼠尾草绿'], ['sand', '暖杏书页'], ['sky', '雾蓝'], ['lavender', '淡紫'], ['rose', '柔粉'], ['gray', '中性灰'], ['custom', '自定义颜色']],
  fontWeight: [[400, '常规 · 400'], [500, '适中 · 500'], [600, '半粗 · 600'], [700, '加粗 · 700']],
  textAlign: [['start', '左对齐 / 跟随阅读方向'], ['justify', '两端对齐'], ['center', '居中对齐']],
  density: [['compact', '紧凑'], ['normal', '舒适'], ['airy', '宽松']],
  lineHeight: [[1.4, '1.4'], [1.6, '1.6'], [1.8, '1.8'], [1.9, '1.9 · 推荐'], [2, '2.0'], [2.2, '2.2'], [2.4, '2.4']],
  letterSpacing: [[0, '标准'], [0.015, '轻微 · 0.015em'], [0.03, '舒展 · 0.03em'], [0.06, '宽松 · 0.06em']],
  textIndent: [[0, '无缩进'], [2, '首行缩进 2 字']],
  cornerRadius: [[0, '直角'], [3, '微圆角 · 3px'], [4, '精细默认 · 4px'], [6, '轻圆角 · 6px'], [9, '圆角 · 9px'], [14, '柔和 · 14px'], [20, '圆润 · 20px']]
};
const guanduNumericKeys = new Set(['fontWeight', 'lineHeight', 'letterSpacing', 'textIndent', 'cornerRadius', 'fontScale']);
const guanduDefaults = {
  fontScale: 1, fontPreset: 'reading', lineHeight: 1.9, style: 'card', theme: 'auto',
  layout: 'below', palette: 'original', fontWeight: 400, textAlign: 'start', density: 'normal',
  letterSpacing: 0.015, textIndent: 0, cornerRadius: 4,
  textColor: '#455b43', backgroundColor: '#f1f2e7', borderColor: '#dce3d0'
};
const guanduDisplayNames = { fontPreset: '译文字体', fontWeight: '文字粗细', palette: '译文配色', style: '底色与边框', layout: '译文排版', lineHeight: '行距', theme: '阅读主题', textAlign: '文字对齐', density: '留白密度', letterSpacing: '字间距', textIndent: '首行缩进', cornerRadius: '圆角' };
const guanduCommon = { fontPreset: 'reading', fontWeight: 400, layout: 'below', textAlign: 'start', lineHeight: 1.9, letterSpacing: 0.015, textIndent: 0, cornerRadius: 4, density: 'normal' };
const guanduPresets = [
  ['经典暖纸', { ...guanduCommon, palette: 'original', style: 'card' }],
  ['雾蓝轻读', { ...guanduCommon, palette: 'sky', layout: 'inset', style: 'card' }],
  ['宋体书页', { ...guanduCommon, palette: 'sand', layout: 'reading', fontPreset: 'songti', style: 'outline', lineHeight: 2, textIndent: 2, letterSpacing: 0.03 }],
  ['重点加粗', { ...guanduCommon, palette: 'sage', fontPreset: 'yahei', fontWeight: 700, style: 'strip' }]
];

function guanduDisplay() { return state.settings.display || {}; }
function guanduChoiceLabel(key, value) {
  const hit = guanduDisplayChoices[key].find(([option]) => String(option) === String(value));
  return hit ? hit[1] : String(value);
}
function guanduField(key, current) {
  return `<div class="field"><label for="display-${key}">${guanduDisplayNames[key]}</label><select id="display-${key}" data-display-key="${key}">${
    guanduDisplayChoices[key].map(([option, label]) => `<option value="${option}" ${String(current[key]) === String(option) ? 'selected' : ''}>${label}</option>`).join('')
  }</select></div>`;
}

function displaySettingsPanel() {
  const d = guanduDisplay();
  return `<div class="setting-panel translation-display-panel"><div class="eyebrow">GUANDU LITE · 译文显示</div><h2 style="margin-top:10px">把译文排成你舒服的样子。</h2>
  <p>这套排版来自观读轻译插件：只改动译文块，原站页面与原文的字体、颜色和结构不受影响。更改自动保存，实时预览与已有译文同步，不重发翻译请求。</p>
  <div class="translation-display-editor">
    <div class="translation-preview-stage" aria-label="译文显示实时预览">
      <div class="display-preview-heading"><span>实时预览</span><span class="display-summary" id="display-summary"></span></div>
      <div class="display-preview-original" lang="en">Every sentence deserves a little room to breathe.</div>
      <div class="gd-translation preview-translation" id="display-preview" lang="zh-CN" data-guandu-owned="translation" data-guandu-heading="false" style="white-space:pre-wrap">每一个词句，都值得慢慢读懂。让译文轻轻陪在原文身旁，给理解留出从容的空间。</div>
    </div>
    <div class="display-presets" role="group" aria-label="译文风格预设">${guanduPresets.map(([label]) => `<button class="btn small" data-display-preset="${label}">${label}</button>`).join('')}</div>
    <div class="appearance-controls">${['layout', 'style', 'palette', 'fontPreset', 'fontWeight', 'lineHeight'].map(key => guanduField(key, d)).join('')}</div>
    <div class="display-weight-size">
      <label class="switch"><input type="checkbox" id="display-bold" ${Number(d.fontWeight) >= 600 ? 'checked' : ''}>加粗译文</label>
      <div class="field"><label for="display-scale">译文字号（相对原文）</label><div class="range-row"><input id="display-scale" type="range" min="50" max="200" step="10" value="${Math.round((d.fontScale || 1) * 100)}" data-display-key="fontScale"><output id="display-scale-value">${Math.round((d.fontScale || 1) * 100)}%</output></div></div>
    </div>
    <details class="display-disclosure" ${d.palette === 'custom' ? 'open' : ''}><summary>自定义文字、底色与边框</summary>
      <p class="caption">选择颜色会自动切换为自定义配色，仅作用于译文。</p>
      <div class="display-colors">${[['textColor', '文字颜色'], ['backgroundColor', '底色'], ['borderColor', '边框 / 强调线']].map(([key, label]) => `<div class="field"><label for="display-color-${key}">${label}</label><div class="display-color-row"><input type="color" id="display-color-${key}" data-display-color="${key}" value="${esc(d[key] || '#000000')}"><input class="input" type="text" maxlength="7" spellcheck="false" data-display-hex="${key}" value="${esc(d[key] || '')}"></div><span class="display-color-error" role="status" hidden>请输入 #RRGGBB，例如 #40566B</span></div>`).join('')}</div>
    </details>
    <details class="display-disclosure"><summary>精细排版 · 对齐、留白、字距与圆角</summary>
      <div class="appearance-controls">${['theme', 'textAlign', 'density', 'letterSpacing', 'textIndent', 'cornerRadius'].map(key => guanduField(key, d)).join('')}</div>
    </details>
    <div class="display-editor-footer"><span class="caption">自动保存 · 只更新显示，不重新翻译</span><button class="btn small" data-action="display-reset">恢复默认显示</button></div>
  </div></div>`;
}

function chatWindowPanel() {
  const w = state.settings.chatWindow || {};
  return `<div class="setting-panel chat-window-panel"><div class="eyebrow">GUANDU LITE · 提问小窗</div><h2 style="margin-top:10px">问一句，不必离开这一页。</h2>
  <p>阅读页右下角的「问」悬浮球打开提问小窗。问答沿用「翻译服务」里的接口地址、模型与密钥，不需要再填一套 Key；只有把翻译服务设为「大模型 · OpenAI 兼容」时才能提问（DeepSeek 官方填 <code>https://api.deepseek.com/v1</code> 与 <code>deepseek-chat</code>）。</p>
  <div class="appearance-controls">
    <div class="field"><label for="window-side">停靠位置</label><select id="window-side" data-window-key="side"><option value="right" ${w.side !== 'left' ? 'selected' : ''}>右下</option><option value="left" ${w.side === 'left' ? 'selected' : ''}>左下</option></select></div>
    <div class="field"><label for="window-width">小窗宽度 · <span id="window-width-value">${w.width || 420}px</span></label><input id="window-width" type="range" min="320" max="900" step="20" value="${w.width || 420}" data-window-key="width"></div>
    <div class="field"><label for="window-height">小窗高度 · <span id="window-height-value">${w.height || 600}px</span></label><input id="window-height" type="range" min="360" max="1000" step="20" value="${w.height || 600}" data-window-key="height"></div>
    <div class="field"><label for="window-ball">提问悬浮球</label><label class="switch"><input id="window-ball" type="checkbox" data-window-key="enabled" ${w.enabled === false ? '' : 'checked'}>在阅读页显示「问」悬浮球</label></div>
  </div>
  <div class="study-tools"><button class="btn small" data-action="window-reset">恢复默认窗口大小</button></div>
  <p class="note">隐藏悬浮球后仍可在阅读页用 Alt+A 切换双语、Alt+X 停止翻译；回答按 Markdown 排版，本页对话不写入学习备份。</p></div>`;
}

function bindGuanduEditor() {
  refreshDisplayPreview();
}

function refreshDisplayPreview() {
  const preview = document.getElementById('display-preview');
  if (!preview || !globalThis.GuanduAppearance) return;
  const stage = preview.closest('.translation-preview-stage');
  GuanduAppearance.applyTranslation(preview, guanduDisplay(), stage || preview.parentElement);
  const summary = document.getElementById('display-summary');
  if (summary) {
    const d = guanduDisplay();
    summary.textContent = guanduChoiceLabel('fontPreset', d.fontPreset) + ' · ' + guanduChoiceLabel('fontWeight', d.fontWeight);
  }
}

async function saveGuanduDisplay(patch) {
  if (!state.settings.display) state.settings.display = { ...patch }; else Object.assign(state.settings.display, patch);
  refreshDisplayPreview();
  refreshTranslationStyles();
  const result = await call('settings', { display: patch });
  if (result && result.settings) setState(result);
}

async function saveGuanduWindow(patch) {
  const result = await call('settings', { chatWindow: patch });
  if (!result) return;
  setState(result);
  GuanduChat.applySettings(result.settings);
}

function refreshTranslationStyles() {
  const viewport = document.getElementById('reader-viewport');
  if (!viewport || !globalThis.GuanduAppearance) return;
  const d = guanduDisplay();
  viewport.querySelectorAll('[data-guandu-owned="translation"]').forEach(node => GuanduAppearance.applyTranslation(node, d, node.parentElement));
}

document.addEventListener('change', async e => {
  const key = e.target.dataset?.displayKey;
  if (key) {
    const numeric = guanduNumericKeys.has(key);
    await saveGuanduDisplay({ [key]: numeric ? Number(e.target.value) : e.target.value });
    return;
  }
  const windowKey = e.target.dataset?.windowKey;
  if (windowKey) {
    const value = windowKey === 'enabled' ? e.target.checked : (windowKey === 'side' ? e.target.value : Number(e.target.value));
    await saveGuanduWindow({ [windowKey]: value });
    return;
  }
  const colorKey = e.target.dataset?.displayColor;
  if (colorKey) {
    await saveGuanduDisplay({ palette: 'custom', [colorKey]: e.target.value });
    const hex = document.querySelector(`[data-display-hex="${colorKey}"]`);
    if (hex) hex.value = e.target.value;
    return;
  }
  const hexKey = e.target.dataset?.displayHex;
  if (hexKey) {
    const error = e.target.closest('.field')?.querySelector('.display-color-error');
    if (!/^#[a-f0-9]{6}$/i.test(e.target.value)) { if (error) error.hidden = false; return; }
    if (error) error.hidden = true;
    await saveGuanduDisplay({ palette: 'custom', [hexKey]: e.target.value.toLowerCase() });
    const picker = document.querySelector(`[data-display-color="${hexKey}"]`);
    if (picker) picker.value = e.target.value;
  }
});

document.addEventListener('input', e => {
  if (e.target.id === 'display-scale') {
    const value = document.getElementById('display-scale-value');
    if (value) value.textContent = e.target.value + '%';
    const merged = { ...guanduDisplay(), fontScale: Number(e.target.value) / 100 };
    Object.assign(state.settings.display, { fontScale: merged.fontScale });
    refreshDisplayPreview();
    return;
  }
  const width = e.target.dataset?.windowKey === 'width' ? document.getElementById('window-width-value') : null;
  if (width) width.textContent = e.target.value + 'px';
  const height = e.target.dataset?.windowKey === 'height' ? document.getElementById('window-height-value') : null;
  if (height) height.textContent = e.target.value + 'px';
  const hexKey = e.target.dataset?.displayHex;
  if (hexKey && /^#[a-f0-9]{6}$/i.test(e.target.value)) {
    Object.assign(state.settings.display, { palette: 'custom', [hexKey]: e.target.value });
    refreshDisplayPreview();
  }
});

document.addEventListener('click', async e => {
  const button = e.target.closest('button');
  if (!button) return;
  const preset = button.dataset?.displayPreset;
  if (preset) {
    const hit = guanduPresets.find(([label]) => label === preset);
    if (hit) {
      await saveGuanduDisplay(hit[1]);
      render();
      toast(`已套用「${preset}」`);
    }
    return;
  }
  if (button.dataset?.action === 'display-reset') {
    await saveGuanduDisplay({ ...guanduDefaults });
    render();
    toast('已恢复默认显示');
    return;
  }
  if (button.dataset?.action === 'window-reset') {
    await saveGuanduWindow({ width: 420, height: 600 });
    render();
    toast('已恢复 420×600，停靠方向与悬浮球设置未改动');
  }
});

document.addEventListener('change', async e => {
  if (e.target.id !== 'display-bold') return;
  await saveGuanduDisplay({ fontWeight: e.target.checked ? 700 : 400 });
});
