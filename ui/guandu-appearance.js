/*
 * 观读轻译 · 双语阅读 1.4.1 的 shared/appearance.js 原样移植。
 * 字体族、配色与译文套用逻辑保持与插件一致；只有插件与扩展通信的最后一块换成 Lumen 的调用方。
 */
(() => {
  'use strict';
  if (globalThis.GuanduAppearance) return;
  const fonts = {
    reading: '"Segoe UI", "PingFang SC", "Microsoft YaHei UI", "Microsoft YaHei", system-ui, sans-serif',
    serif: 'Georgia, "Noto Serif CJK SC", "Source Han Serif SC", "Songti SC", "STSong", "SimSun", serif',
    system: 'system-ui, "PingFang SC", "Microsoft YaHei", sans-serif',
    yahei: '"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", system-ui, sans-serif',
    songti: '"Noto Serif SC", "Source Han Serif SC", "Songti SC", "SimSun", serif',
    kaiti: '"KaiTi", "STKaiti", "Kaiti SC", "Noto Serif SC", serif',
    humanist: 'Aptos, Calibri, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
    mono: '"Cascadia Code", Consolas, "Sarasa Mono SC", "Microsoft YaHei", monospace'
  };
  // Each entry is background / border / ink / accent, in light and dark variants.
  const palettes = {
    original: { light: ['#f1f2e7','#dce3d0','#455b43','#81976c'], paper: ['#f4f0df','#e3ddc8','#5d6249','#9a9d77'], dark: ['#2c392d','#46583e','#d5e1c7','#99b17d'] },
    sage: { light: ['#edf2e8','#d7e3cd','#405a3d','#839c70'], dark: ['#293b2b','#455e40','#d4e6c9','#a2bd8a'] },
    sand: { light: ['#f8f0e0','#e7d9bd','#665437','#b29a6c'], dark: ['#3b3226','#61543f','#ebdab7','#c9ad7b'] },
    sky: { light: ['#edf3f8','#d6e2ed','#3c5a70','#86a4ba'], dark: ['#263744','#40596d','#d1e3f1','#91b5d0'] },
    lavender: { light: ['#f2eef9','#dfd5ee','#5d4c78','#aa96c2'], dark: ['#352c45','#59486f','#e0d5ef','#bda4d9'] },
    rose: { light: ['#faeef0','#efd8dd','#76515d','#c39aa6'], dark: ['#402d35','#674552','#efd9e0','#d1a0b2'] },
    gray: { light: ['#f1f2f2','#dce0df','#4b5553','#929f9b'], dark: ['#303736','#505c59','#dce4e1','#a6b5af'] }
  };
  const bounded = (value, fallback, min, max) => typeof value === 'number' && Number.isFinite(value) ? Math.max(min,Math.min(max,value)) : fallback;
  const choose = (value, values, fallback) => values.includes(value) ? value : fallback;
  const hex = (value, fallback) => typeof value === 'string' && /^#[a-f0-9]{6}$/i.test(value) ? value : fallback;
  const normalize = (value = {}) => ({
    fontPreset: Object.hasOwn(fonts,value.fontPreset) ? value.fontPreset : 'reading',
    lineHeight: bounded(value.lineHeight,1.9,1.4,2.4),
    style: choose(value.style,['card','rule','plain','outline','strip'],'card'),
    theme: choose(value.theme,['auto','paper','light','dark'],'auto'),
    layout: choose(value.layout,['below','inset','reading'],'below'),
    palette: choose(value.palette,[...Object.keys(palettes),'custom'],'original'),
    fontWeight: choose(value.fontWeight,[400,500,600,700],400),
    textAlign: choose(value.textAlign,['start','justify','center'],'start'),
    density: choose(value.density,['compact','normal','airy'],'normal'),
    letterSpacing: bounded(value.letterSpacing,.015,0,.12),
    textIndent: bounded(value.textIndent,0,0,3), cornerRadius: bounded(value.cornerRadius,4,0,24),
    textColor: hex(value.textColor,'#455b43'), backgroundColor: hex(value.backgroundColor,'#f1f2e7'), borderColor: hex(value.borderColor,'#dce3d0')
  });
  function resolveTheme(value, source) {
    const config = normalize(value);
    if (config.theme !== 'auto') return config.theme;
    let parent = source || document.body;
    while (parent) {
      const color = getComputedStyle(parent).backgroundColor.match(/[\d.]+/g)?.map(Number);
      if (color?.length >= 3 && (color.length < 4 || color[3] > .7)) return (color[0]*.2126+color[1]*.7152+color[2]*.0722) < 115 ? 'dark' : 'light';
      parent = parent.parentElement;
    }
    return getComputedStyle(document.documentElement).colorScheme === 'dark' ? 'dark' : 'light';
  }
  function colors(value, tone) {
    const config = normalize(value);
    if (config.palette === 'custom') return [config.backgroundColor,config.borderColor,config.textColor,config.borderColor];
    const palette = palettes[config.palette];
    return palette[tone] || palette[tone === 'dark' ? 'dark' : 'light'];
  }
  function applyTranslation(node, value = {}, source) {
    const config = normalize(value), heading = node.dataset.guanduHeading === 'true';
    const tone = resolveTheme(value,source || node.parentElement), palette = colors(config,tone);
    node.dataset.guanduStyle = config.style; node.dataset.guanduTheme = tone;
    node.dataset.guanduLayout = config.layout; node.dataset.guanduPalette = config.palette;
    for (const [i,key] of ['--gd-paper','--gd-line','--gd-ink','--gd-accent'].entries()) node.style.setProperty(key,palette[i]);
    const density = {compact:[8,11,.35,.1],normal:[12,16,.6,.2],airy:[18,22,.85,.4]}[config.density];
    node.style.setProperty('--gd-padding-block',density[0]+'px'); node.style.setProperty('--gd-padding-inline',density[1]+'px');
    node.style.setProperty('--gd-radius',config.cornerRadius+'px');
    node.style.setProperty('--gd-compact-radius',config.cornerRadius+'px');
    node.style.marginBlockStart = density[2]+'em'; node.style.marginBlockEnd = density[3]+'em';
    node.style.fontFamily = fonts[config.fontPreset];
    node.style.lineHeight = String(heading ? Math.min(config.lineHeight,1.6) : config.lineHeight);
    node.style.fontWeight = String(heading ? Math.max(500,config.fontWeight) : config.fontWeight);
    node.style.fontSynthesis = config.fontWeight >= 600 ? 'weight' : 'none';
    node.style.fontStyle = 'normal'; node.style.textAlign = config.textAlign;
    node.style.letterSpacing = (heading && config.letterSpacing === .015 ? 0 : config.letterSpacing)+'em'; node.style.textIndent = (heading ? 0 : config.textIndent)+'em';
    node.style.fontSize = `${(value.fontScale || 1) * (heading ? .74 : 1)}em`;
    node.style.maxWidth = config.layout === 'reading' ? '34em' : '100%';
    node.style.marginInlineStart = config.layout === 'reading' ? 'auto' : config.layout === 'inset' ? '1.25em' : '0px';
    node.style.marginInlineEnd = config.layout === 'reading' ? 'auto' : '0px';
  }
  function applyLayer(layer, value) {
    const config = normalize(value);
    layer.dataset.theme = config.theme;
    layer.style.setProperty('--reading-font',fonts[config.fontPreset]);
    layer.style.setProperty('--reading-leading',String(config.lineHeight));
    layer.style.setProperty('--reading-weight',String(config.fontWeight));
    layer.style.setProperty('--reading-synthesis',config.fontWeight >= 600 ? 'weight' : 'none');
    layer.style.setProperty('--reading-spacing',config.letterSpacing+'em');
    const palette = colors(config,resolveTheme(value,document.body));
    layer.style.setProperty('--selection-paper',config.palette === 'original' ? 'var(--sunken)' : palette[0]);
    layer.style.setProperty('--selection-ink',config.palette === 'original' ? 'var(--text)' : palette[2]);
    layer.style.setProperty('--selection-line',config.palette === 'original' ? 'var(--separator)' : palette[1]);
  }
  Object.defineProperty(globalThis,'GuanduAppearance',{value:Object.freeze({normalize,applyTranslation,applyLayer,fonts,palettes,colors,resolveTheme})});
})();
