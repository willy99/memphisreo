import { useTranslation } from "react-i18next";
import { useTheme } from "../theme/ThemeContext";

export function ThemeToggle() {
  const { t } = useTranslation();
  const { theme, toggle } = useTheme();

  return (
    <button className="icon-button" onClick={toggle} aria-label={t("nav.theme")} title={t("nav.theme")}>
      {theme === "dark" ? "☀️" : "🌙"}
    </button>
  );
}
