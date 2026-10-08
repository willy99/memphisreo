import { Link, Outlet, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { ThemeToggle } from "../../components/ThemeToggle";
import { BrandMark } from "../../components/AuthScreen";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "../../api/client";
import type { AgencyInfo } from "../../api/types";

const AgencyContext = createContext<AgencyInfo | null>(null);
export const useAgency = () => useContext(AgencyContext);

/** Публічна оболонка: шапка агенції, без автентифікації. */
export function PublicLayout({ children }: { children?: ReactNode }) {
  const { t } = useTranslation();
  const { slug } = useParams();
  const [agency, setAgency] = useState<AgencyInfo | null>(null);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    if (!slug) return;
    api.get<AgencyInfo>(`/api/public/agencies/${slug}`, "").then(setAgency).catch(() => setMissing(true));
  }, [slug]);

  if (missing) {
    return (
      <div className="public-shell">
        <main className="public-main">
          <div className="empty-state">
            <h2>{t("public.agencyNotFound")}</h2>
          </div>
        </main>
      </div>
    );
  }

  return (
    <AgencyContext.Provider value={agency}>
      <div className="public-shell">
        <header className="public-header">
          <Link to={`/p/${slug}`} className="brand">
            <BrandMark />
            {agency?.name ?? "…"}
          </Link>
          <div className="public-header-right">
            {agency?.phone && (
              <a className="public-phone" href={`tel:${agency.phone.replace(/\s/g, "")}`}>
                {agency.phone}
              </a>
            )}
            <LanguageSwitcher />
            <ThemeToggle />
          </div>
        </header>
        <main className="public-main">{children ?? <Outlet />}</main>
        <footer className="public-footer">
          <span>
            © {new Date().getFullYear()} {agency?.name}
            {agency?.city && <> · {agency.city}</>}
          </span>
          <span className="hint">{t("public.poweredBy")}</span>
        </footer>
      </div>
    </AgencyContext.Provider>
  );
}
