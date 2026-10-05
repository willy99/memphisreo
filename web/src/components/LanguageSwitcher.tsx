import { useTranslation } from "react-i18next";
import { SUPPORTED_LANGUAGES } from "../i18n";
import { IconDropdown } from "./NavDropdown";

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();

  return (
    <IconDropdown icon={<span>🌐</span>} label={t("nav.language")}>
      {(close) => (
        <>
          {SUPPORTED_LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              className={`dropdown-item ${i18n.language === lang.code ? "active" : ""}`}
              onClick={() => {
                i18n.changeLanguage(lang.code);
                close();
              }}
            >
              {lang.label}
            </button>
          ))}
        </>
      )}
    </IconDropdown>
  );
}
