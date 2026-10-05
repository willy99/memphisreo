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
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!token) return;
    api
      .get<Tenant>("/api/tenant", token)
      .then((tn) => {
        setTenant(tn);
        setName(tn.name);
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
      const updated = await api.patch<Tenant>("/api/tenant", { name }, token);
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
        {error && <p className="error">{error}</p>}
        {saved && <p className="hint">{t("tenantSettings.saved")}</p>}
        <button type="submit">{t("tenantSettings.save")}</button>
      </form>
    </div>
  );
}
