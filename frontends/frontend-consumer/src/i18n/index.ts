import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { Platform } from 'react-native';

import de from './locales/de.json';
import en from './locales/en.json';
import fr from './locales/fr.json';
import it from './locales/it.json';

export const SUPPORTED_LANGUAGES = ['de', 'fr', 'it', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const LANGUAGE_NAMES: Record<Language, string> = {
  de: 'Deutsch',
  fr: 'Français',
  it: 'Italiano',
  en: 'English',
};

const FALLBACK_LANGUAGE: Language = 'de';
const STORAGE_KEY = 'lang';

function supportedLanguageOf(tag: string | null | undefined): Language | null {
  const base = String(tag ?? '').toLowerCase().split(/[-_]/)[0];
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(base) ? (base as Language) : null;
}

function storedLanguage(): string | null {
  try {
    return globalThis.localStorage?.getItem(STORAGE_KEY) ?? null;
  } catch {
    return null;
  }
}

function deviceLanguages(): readonly string[] {
  if (typeof navigator !== 'undefined' && navigator.languages?.length) {
    return navigator.languages;
  }
  return [Intl.DateTimeFormat().resolvedOptions().locale];
}

function detectLanguage(): Language {
  return supportedLanguageOf(storedLanguage())
    ?? deviceLanguages().map(supportedLanguageOf).find((lng): lng is Language => lng !== null)
    ?? FALLBACK_LANGUAGE;
}

function rememberLanguage(lng: Language): void {
  try {
    globalThis.localStorage?.setItem(STORAGE_KEY, lng);
  } catch {
    return;
  }
}

export function currentLanguage(): Language {
  return supportedLanguageOf(i18n.resolvedLanguage) ?? FALLBACK_LANGUAGE;
}

export function currentLocale(): string {
  return `${currentLanguage()}-CH`;
}

export function chooseLanguage(lng: Language): void {
  rememberLanguage(lng);
  void i18n.changeLanguage(lng);
}

i18n.on('languageChanged', () => {
  if (Platform.OS === 'web' && typeof document !== 'undefined') {
    document.documentElement.lang = currentLocale();
  }
});

void i18n.use(initReactI18next).init({
  resources: {
    de: { translation: de },
    fr: { translation: fr },
    it: { translation: it },
    en: { translation: en },
  },
  lng: detectLanguage(),
  fallbackLng: FALLBACK_LANGUAGE,
  supportedLngs: [...SUPPORTED_LANGUAGES],
  load: 'languageOnly',
  initAsync: false,
  // React escapes rendered text; i18next must not escape it a second time.
  interpolation: { escapeValue: false },
  // No backend: saveMissing only routes missing keys to this handler.
  saveMissing: true,
  missingKeyHandler: (_lngs, _ns, key) => {
    console.warn(`[i18n] missing key "${key}"`);
  },
});

export default i18n;
