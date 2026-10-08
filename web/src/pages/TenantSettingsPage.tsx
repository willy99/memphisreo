import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { Tenant } from "../api/types";

export function TenantSettingsPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [name, setName] = useState("");
  const [pub, setPub] = useState({ publicPhone: "", publicEmail: "", website: "", about: "", publicCity: "" });
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!token) return;
    api
      .get<Tenant>("/api/tenant", token)
      .then((tn) => {
        setTenant(tn);
        setName(tn.name);
        setPub({ publicPhone: tn.publicPhone ?? "", publicEmail: tn.publicEmail ?? "", website: tn.website ?? "", about: tn.about ?? "", publicCity: tn.publicCity ?? "" });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("tenantSettings.loadError")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) return;
    setError(null);
    setSaved(false);
    try {
      const updated = await api.patch<Tenant>("/api/tenant", { name, ...pub }, token);
      setTenant(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("tenantSettings.saveError"));
    }
  }

  if (!tenant) {
    return <div className="page">{error ? <p className="error">{error}</p> : t("tenantSettings.loading")}</div>;
  }

  return (
    <div className="page page-narrow">
      <h1>{t("tenantSettings.title")}</h1>
      <form onSubmit={handleSubmit} className="form">
        <label>
          {t("tenantSettings.name")}
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <p className="hint">
          {t("tenantSettings.meta", { country: tenant.countryCode, region: tenant.region, status: tenant.status })}
        </p>
        <h2>{t("tenantSettings.publicTitle")}</h2>
        <p className="hint">
          {t("tenantSettings.publicHint")}{" "}
          <a href={`/p/${tenant.slug}`} target="_blank" rel="noreferrer">
            /p/{tenant.slug}
          </a>
        </p>
        <label>
          {t("tenantSettings.publicPhone")}
          <input type="tel" value={pub.publicPhone} onChange={(e) => setPub({ ...pub, publicPhone: e.target.value })} />
        </label>
        <label>
          {t("tenantSettings.publicEmail")}
          <input type="email" value={pub.publicEmail} onChange={(e) => setPub({ ...pub, publicEmail: e.target.value })} />
        </label>
        <label>
          {t("tenantSettings.publicCity")}
          <input value={pub.publicCity} onChange={(e) => setPub({ ...pub, publicCity: e.target.value })} />
        </label>
        <label>
          {t("tenantSettings.website")}
          <input type="url" value={pub.website} onChange={(e) => setPub({ ...pub, website: e.target.value })} />
        </label>
        <label>
          {t("tenantSettings.about")}
          <textarea rows={4} value={pub.about} onChange={(e) => setPub({ ...pub, about: e.target.value })} />
        </label>
        {error && <p className="error">{error}</p>}
        {saved && <p className="hint">{t("tenantSettings.saved")}</p>}
        <button type="submit">{t("tenantSettings.save")}</button>
      </form>
    </div>
  );
}
