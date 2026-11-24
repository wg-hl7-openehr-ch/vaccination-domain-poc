(function () {
  const SUPPORTED_LANGS = ['de', 'fr', 'it', 'en'];
  const FALLBACK_LANG = 'de';
  const RESOURCES = window.I18N_RESOURCES || {};
  const listeners = new Set();

  function supportedLanguageOf(tag) {
    const base = String(tag || '').toLowerCase().split(/[-_]/)[0];
    return SUPPORTED_LANGS.includes(base) ? base : null;
  }

  function storedLanguage() {
    try {
      return localStorage.getItem('lang');
    } catch (err) {
      return null;
    }
  }

  function browserLanguages() {
    return navigator.languages && navigator.languages.length
      ? navigator.languages
      : [navigator.language];
  }

  function detectLanguage() {
    return supportedLanguageOf(storedLanguage())
      || browserLanguages().map(supportedLanguageOf).find(Boolean)
      || FALLBACK_LANG;
  }

  let currentLanguage = detectLanguage();

  function lookup(lng, key) {
    const value = key.split('.').reduce(function (node, part) {
      return node ? node[part] : undefined;
    }, RESOURCES[lng]);
    return typeof value === 'string' ? value : undefined;
  }

  function resolve(lng, key, params) {
    if (params && typeof params.count === 'number') {
      const category = new Intl.PluralRules(lng).select(params.count);
      return lookup(lng, key + '_' + category) ?? lookup(lng, key + '_other');
    }
    return lookup(lng, key);
  }

  function interpolate(text, params) {
    return text.replace(/\{\{\s*(\w+)\s*\}\}/g, function (placeholder, name) {
      return params && params[name] != null ? String(params[name]) : placeholder;
    });
  }

  function t(key, params) {
    const text = resolve(currentLanguage, key, params) ?? resolve(FALLBACK_LANG, key, params);
    if (text === undefined) {
      console.warn('[i18n] missing key "' + key + '"');
      return key;
    }
    return interpolate(text, params);
  }

  function exists(key) {
    return lookup(currentLanguage, key) !== undefined || lookup(FALLBACK_LANG, key) !== undefined;
  }

  function getLanguage() {
    return currentLanguage;
  }

  function getLocale() {
    return currentLanguage + '-CH';
  }

  function rememberLanguage(lng) {
    try {
      localStorage.setItem('lang', lng);
    } catch (err) {
      return;
    }
  }

  function applyLanguage(lng) {
    currentLanguage = lng;
    document.documentElement.lang = getLocale();
    listeners.forEach(function (listener) { listener(); });
  }

  function chooseLanguage(tag) {
    const lng = supportedLanguageOf(tag);
    if (!lng) return;
    rememberLanguage(lng);
    applyLanguage(lng);
  }

  function subscribe(listener) {
    listeners.add(listener);
    return function () { listeners.delete(listener); };
  }

  applyLanguage(currentLanguage);

  window.I18n = { SUPPORTED_LANGS, t, exists, getLanguage, getLocale, chooseLanguage, subscribe };
})();
