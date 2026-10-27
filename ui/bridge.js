// Bridge between the original renderer API (window.lumen) and Wails bindings.
(function () {
  const methodByChannel = {
    state: 'State',
    refresh: 'Refresh',
    settings: 'Settings',
    'reader-open': 'ReaderOpen',
    'app-restart': 'Restart',
    'reader-bounds': 'ReaderBounds',
    'reader-hide': 'ReaderHide',
    'reader-control': 'ReaderControl',
    external: 'External',
    lookup: 'Lookup',
    translate: 'Translate',
    'vocab-add': 'VocabAdd',
    'vocab-delete': 'VocabDelete',
    'vocab-review': 'VocabReview',
    'saved-toggle': 'SavedToggle',
    export: 'Export',
    import: 'Import',
    'translation-config': 'TranslationConfig',
    'translation-test': 'TranslationTest',
    'plugins': 'Plugins',
    'plugin-toggle': 'PluginToggle',
    'plugin-install': 'PluginInstall',
    'plugin-remove': 'PluginRemove',
    'plugin-open-folder': 'PluginOpenFolder',
    'deepseek-config': 'DeepSeekConfig',
    'deepseek-ask': 'DeepSeekAsk',
    'deepseek-show': 'DeepSeekShow',
    'deepseek-hide': 'DeepSeekHide',
    'deepseek-bounds': 'DeepSeekBounds',
    'deepseek-reload': 'DeepSeekReload',
    'deepseek-popout': 'DeepSeekPopout',
    'deepseek-copy-context': 'ChatCopyContext',
    'open-edge': 'OpenEdge',
    'reader-extract': 'ReaderExtract',
    'article-extract': 'ExtractArticle',
    'reader-show': 'ReaderShow',
    'reader-restore-view': 'ReaderRestoreView',
    'auto-translation-authorize': 'AutoTranslationAuthorize',
    'chat-show': 'ChatShow',
    'chat-hide': 'ChatHide',
    'chat-bounds': 'ChatBounds',
    'chat-reload': 'ChatReload',
    'chat-popout': 'ChatPopout',
    'chat-copy-context': 'ChatCopyContext',
    'history-delete': 'HistoryDelete',
    'history-clear': 'HistoryClear',
    'storage-info': 'StorageInfo',
    'storage-settings': 'StorageSettings',
    'storage-clear': 'StorageClear'
  };

  const allowedEvents = new Set([
    'feed-progress',
    'library-updated',
    'reader-state',
    'selection',
    'chat-status',
    'deepseek-status',
    'translation-error'
  ]);

  function bindings() {
    const app = window.go && window.go.main && window.go.main.App;
    if (!app) throw new Error('Wails 绑定尚未就绪');
    return app;
  }

  async function call(channel, ...args) {
    const method = methodByChannel[channel];
    if (!method) throw new Error('Unsupported action: ' + channel);
    const fn = bindings()[method];
    if (typeof fn !== 'function') throw new Error('未实现的接口: ' + channel);
    return fn(...args);
  }

  function on(channel, callback) {
    if (!allowedEvents.has(channel)) return () => {};
    const runtime = window.runtime;
    if (!runtime || typeof runtime.EventsOn !== 'function') return () => {};
    return runtime.EventsOn(channel, callback);
  }

  window.lumen = { call, on };
})();
