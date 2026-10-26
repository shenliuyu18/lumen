function pluginPrompt(){
  return `<div class="plugin-prompt"><div><div class="eyebrow">PLUGINS REQUIRED</div><h3>先安装来源插件。</h3><p>Lumen 不内置爬虫。导入官方爬虫 ZIP 后，媒体目录、各站前三与推荐更新才会启用；去广告插件可单独安装。</p></div><button class="btn primary" data-nav="plugins">打开插件中心 ${icon('arrow')}</button></div>`;
}
function pluginsPage(){
  const plugins=state.plugins||[];
  return `${heading('PLUGINS','把来源与阅读体验，交给插件。','Lumen 不内置来源或广告规则。导入官方 ZIP，或使用声明式 JS 生成自己的插件；桌面和安卓共用同一份包。')}
  <div class="study-tools" style="margin-bottom:18px">
    <button class="btn" data-action="plugin-install">${icon('plus')}导入 ZIP / JS</button>
    <button class="btn small" data-action="plugin-open-folder">打开插件目录</button>
  </div>
  <p class="note">官方插件包随发行版提供，首次使用时在下方导入 <code>lumen-crawler-*.zip</code> 与 <code>lumen-adblock-*.zip</code>。停用爬虫插件后，刷新不会新增来源；已缓存文章仍保留。</p>
  <div class="plugin-grid">
  ${plugins.map(p=>`<div class="plugin-card">
    <div class="plugin-head"><span class="plugin-type">${p.type==='crawler'?'爬虫':'去广告'}</span><strong>${esc(p.name)}</strong><span class="subtle">v${esc(p.version)}</span></div>
    <p>${esc(p.description||'')}</p>
    <div class="plugin-meta">${p.author==='Lumen'?'官方插件':'用户插件'}${p.type==='crawler'?` · ${p.sourceCount||0} 个来源`:` · ${p.ruleCount||0} 条规则`}${p.license?` · ${esc(p.license)}`:''}${p.author&&p.author!=='Lumen'?` · ${esc(p.author)}`:''}</div>
    <div class="study-tools">
      <label class="switch"><input type="checkbox" data-plugin-toggle="${esc(p.id)}" ${p.enabled?'checked':''}>启用</label>
      <button class="btn small danger" data-plugin-remove="${esc(p.id)}">移除</button>
    </div>
  </div>`).join('')||empty('还没有安装插件','Lumen 不内置来源与广告规则。导入官方爬虫 ZIP 后，媒体目录、各站前三和推荐更新才会启用。','<button class="btn primary" data-action="plugin-install">导入第一个插件</button>')}
  </div>
  <div class="divider"></div>
  <p class="note">ZIP：根目录或一级目录内包含 plugin.json，type 为 crawler 或 adblock。JS：使用 <code>module.exports = {...}</code>、<code>export default {...}</code> 或 <code>window.__LUMEN_PLUGIN__ = {...}</code> 导出同一个 JSON 对象；Lumen 只解析对象，不执行插件脚本。去广告规则只针对页面 DOM，不修改原站内容，也不绕过登录或付费墙。</p>`;
}

document.addEventListener('change',async e=>{
  const toggle=e.target.dataset?.pluginToggle;
  if(!toggle)return;
  const s=await api.call('plugin-toggle',toggle,e.target.checked);
  if(s){setState(s);render();toast(e.target.checked?'插件已启用':'插件已停用');}
});

document.addEventListener('click',async e=>{
  const b=e.target.closest('button');if(!b)return;
  if(b.dataset.action==='plugin-install'){
    const s=await api.call('plugin-install');
    if(s){setState(s);render();toast('插件已安装并启用');}
    return;
  }
  if(b.dataset.action==='plugin-open-folder'){await api.call('plugin-open-folder');return;}
  if(b.dataset.pluginRemove){
    if(!confirm('移除这个插件？它提供的来源和规则会立即失效。'))return;
    const s=await api.call('plugin-remove',b.dataset.pluginRemove);
    if(s&&s.error)toast(s.error);
    else if(s){setState(s);render();toast('插件已移除');}
  }
});
