import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../../api/client";
import type { OwnerReport } from "../../api/types";
import { StatusPill } from "../../components/StatusPill";
import { Timeline } from "../../components/Timeline";
import { BrandMark } from "../../components/AuthScreen";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { ThemeToggle } from "../../components/ThemeToggle";
import { addressLine, money } from "./publicFormat";

/** Кабінет власника за посиланням: лише свої об'єкти, лише owner_visible події. */
export function OwnerPage() {
  const { t, i18n } = useTranslation();
  const { token } = useParams();
  const [report, setReport] = useState<OwnerReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    api.get<OwnerReport>(`/api/public/owner/${token}`, "").then(setReport).catch(() => setError(t("owner.invalid")));
  }, [token, t]);

  return (
    <div className="public-shell">
      <header className="public-header">
        <span className="brand">
          <BrandMark />
          {report?.agencyName ?? "Memphis"}
        </span>
        <div className="public-header-right">
          {report?.agencyPhone && <a className="public-phone" href={`tel:${report.agencyPhone.replace(/\s/g, "")}`}>{report.agencyPhone}</a>}
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>
      <main className="public-main owner-main">
        {error && (
          <div className="empty-state">
            <h2>{error}</h2>
            <p className="hint">{t("owner.invalidHint")}</p>
          </div>
        )}
        {report && (
          <>
            <h1>{t("owner.hello", { name: report.ownerFirstName })}</h1>
            <p className="page-subtitle">{t("owner.subtitle", { count: report.properties.length })}</p>
            {report.properties.map((p) => (
              <section key={p.id} className="panel owner-property">
                <div className="owner-head">
                  {p.coverUrl && <img src={p.coverUrl} alt="" className="owner-cover" />}
                  <div>
                    <h2>{p.title ?? t(`propertyTypes.${p.type}`)}</h2>
                    <p className="hint">{addressLine({ ...p, complexName: null })}</p>
                    <p>
                      <StatusPill status={p.status} /> <strong>{money(p.price, p.currency, i18n.language)}</strong>
                    </p>
                  </div>
                </div>
                <h3 className="form-section">{t("owner.timeline")}</h3>
                <Timeline entries={p.timeline} emptyText={t("owner.noEvents")} />
              </section>
            ))}
          </>
        )}
      </main>
    </div>
  );
}
