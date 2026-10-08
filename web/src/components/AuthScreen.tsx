import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { LanguageSwitcher } from "./LanguageSwitcher";
import { ThemeToggle } from "./ThemeToggle";

interface AuthScreenProps {
  title: string;
  subtitle?: string;
  /** Позначка над заголовком, напр. "Платформа" для входу супер-адміна. */
  badge?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * Спільний екран автентифікації: фото нерухомості на весь екран, форма —
 * по центру на "склі". Використовують логін, вхід адміна, скидання пароля,
 * прийняття запрошення.
 */
export function AuthScreen({ title, subtitle, badge, children, footer }: AuthScreenProps) {
  const { t } = useTranslation();

  return (
    <div className="auth-screen">
      <div className="auth-backdrop" aria-hidden="true" />
      <header className="auth-topline">
        <span className="auth-brand">
          <BrandMark />
          {t("app.name")}
        </span>
        <div className="auth-controls">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>

      <main className="auth-center">
        <section className="auth-card">
          {badge && <span className="auth-badge">{badge}</span>}
          <h1>{title}</h1>
          {subtitle && <p className="auth-subtitle">{subtitle}</p>}
          {children}
          {footer && <div className="auth-footer">{footer}</div>}
        </section>
      </main>

      <p className="auth-tagline">{t("app.tagline")}</p>
    </div>
  );
}

export function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
      <path d="M3 11.2 12 4l9 7.2V20a1 1 0 0 1-1 1h-5.5v-6h-5v6H4a1 1 0 0 1-1-1z" fill="currentColor" />
    </svg>
  );
}
