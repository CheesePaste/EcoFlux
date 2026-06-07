import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import type { Lang } from "./translations";
import { translations, getSavedLang, saveLang } from "./translations";

interface I18nContextValue {
  t: (key: string) => string;
  lang: Lang;
  setLang: (lang: Lang) => void;
  toggleLang: () => void;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(getSavedLang);

  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    saveLang(l);
  }, []);

  const toggleLang = useCallback(() => {
    setLangState((prev) => {
      const next = prev === "zh" ? "en" : "zh";
      saveLang(next);
      return next;
    });
  }, []);

  const value: I18nContextValue = {
    t: (key: string) => (translations[lang] as unknown as Record<string, string>)[key] ?? key,
    lang,
    setLang,
    toggleLang,
  };

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useT(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useT must be used within I18nProvider");
  return ctx;
}
