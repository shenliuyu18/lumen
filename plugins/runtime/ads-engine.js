(function () {
  var rules = window.__LUMEN_AD_RULES__ || { global: {}, domains: {} };
  if (window.__lumenAdblockInstalled) {
    if (window.__lumenAdblockApply) window.__lumenAdblockApply();
    return;
  }
  window.__lumenAdblockInstalled = true;
  var totalRemoved = 0;

  var host = (location.hostname || '').replace(/^www\./, '').toLowerCase();
  var selectors = ((rules.global && rules.global.selectors) || []).slice();
  var patterns = ((rules.global && rules.global.textPatterns) || []).slice();
  Object.keys(rules.domains || {}).forEach(function (domain) {
    var normalized = String(domain).replace(/^www\./, '').toLowerCase();
    if (host === normalized || host.endsWith('.' + normalized)) {
      selectors = selectors.concat((rules.domains[domain].selectors || []));
      patterns = patterns.concat((rules.domains[domain].textPatterns || []));
    }
  });
  selectors = selectors.concat([
    'iframe[src*="doubleclick"]',
    'iframe[src*="googlesyndication"]',
    'iframe[src*="taboola"]',
    'iframe[src*="outbrain"]',
    '[id*="div-gpt-ad"]',
    '[class*="gpt-ad"]',
    'ins.adsbygoogle'
  ]);
  selectors = Array.from(new Set(selectors.filter(Boolean)));
  patterns = Array.from(new Set(patterns.filter(Boolean).map(function (p) { return String(p).toLowerCase(); })));

  var proseTags = { P: 1, BLOCKQUOTE: 1, LI: 1, H1: 1, H2: 1, H3: 1, H4: 1, H5: 1, H6: 1, PRE: 1, FIGCAPTION: 1 };
  var adContainerSelector = '[data-ad],[data-ad-slot],[data-ad-unit],[data-advertisement],[class*="advert"],[class*="ad-slot"],[class*="ad-unit"],[class*="ad-container"],[class*="adsbygoogle"],[class*="sponsor"],[id*="advert"],[id^="div-gpt-ad"]';

  function protectedElement(el) {
    if (!el || !el.tagName) return true;
    if (['HTML', 'BODY', 'MAIN', 'ARTICLE'].indexOf(el.tagName) !== -1) return true;
    if (proseTags[el.tagName] && !el.closest(adContainerSelector)) return true;
    var body = el.closest('article,[itemprop="articleBody"],main');
    if (body && (el.textContent || '').length > 240) return true;
    return false;
  }

  function removeNode(el) {
    if (!el || protectedElement(el)) return 0;
    if (el.getAttribute('data-lumen-adblock-hidden') === '1') return 0;
    el.setAttribute('data-lumen-adblock-hidden', '1');
    el.style.setProperty('display', 'none', 'important');
    el.remove();
    return 1;
  }

  function removeAll(nodes, tally) {
    for (var i = 0; i < nodes.length; i++) tally.removed += removeNode(nodes[i]);
  }

  function apply() {
    // 规则有几百条，逐条 querySelectorAll 会让每次 DOM 变动都很慢：先按 120 条拼成
    // 一个逗号选择器批量查询，整批语法出错时再退回逐条执行。
    var removed = 0;
    var tally = { removed: 0 };
    for (var start = 0; start < selectors.length; start += 120) {
      var chunk = selectors.slice(start, start + 120);
      try {
        removeAll(document.querySelectorAll(chunk.join(',')), tally);
      } catch (_) {
        chunk.forEach(function (selector) {
          try {
            removeAll(document.querySelectorAll(selector), tally);
          } catch (_) {}
        });
      }
    }
    removed = tally.removed;
    if (patterns.length) {
      var candidates = document.querySelectorAll('div,aside,section,span,p,a,button');
      for (var i = 0; i < candidates.length; i++) {
        var el = candidates[i];
        var text = (el.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
        if (!text || text.length > 60) continue;
        var matched = patterns.some(function (p) { return text.indexOf(p) !== -1; });
        if (!matched) continue;
        if (proseTags[el.tagName]) continue;
        if (el.querySelector('p,li,blockquote,h1,h2,h3,h4,h5,h6')) continue;
        var container = el.closest(adContainerSelector);
        removed += removeNode(container || el);
      }
    }
    totalRemoved += removed;
    document.documentElement.setAttribute('data-lumen-adblock', String(totalRemoved));
    return removed;
  }

  window.__lumenAdblockApply = apply;
  var scheduled = false;
  var observer = new MutationObserver(function () {
    if (scheduled) return;
    scheduled = true;
    setTimeout(function () { scheduled = false; apply(); }, 240);
  });
  try {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  } catch (_) {}
  apply();
})();
