import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en.json";
import uk from "./locales/uk.json";

export const SUPPORTED_LANGUAGES = [
  { code: "uk", label: "Українська" },
  { code: "en", label: "English" },
  // Наступні європейські мови додаються тим самим способом — новий locales/*.json + рядок тут.
] as const;

const STORAGE_KEY = "memphisreo-language";

function readInitialLanguage(): string {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored && SUPPORTED_LANGUAGES.some((l) => l.code === stored)) return stored;
  const browserLang = navigator.language.slice(0, 2);
  return SUPPORTED_LANGUAGES.some((l) => l.code === browserLang) ? browserLang : "uk";
}

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    uk: { translation: uk },
  },
  lng: readInitialLanguage(),
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

i18n.on("languageChanged", (lng) => {
  localStorage.setItem(STORAGE_KEY, lng);
});

export default i18n;
