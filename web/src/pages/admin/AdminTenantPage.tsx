import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { usePlatformAuth } from "../../auth/PlatformAuthContext";
import { api, ApiError } from "../../api/client";
import type { PlatformPropertyRow, TenantSummary } from "../../api/types";
import { StatusPill } from "../../components/StatusPill";
import { AgentsIcon, PropertiesIcon, StatCard } from "../../components/StatCard";

export function AdminTenantPage() {
  const { t, i18n } = useTranslation();
  const { tenantId } = useParams();
  const { token } = usePlatformAuth();
  const [tenant, setTenant] = useState<TenantSummary | null>(null);
  const [properties, setProperties] = useState<PlatformPropertyRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token || !tenantId) return;
    Promise.all([
      api.get<TenantSummary>(`/platform-admin/tenants/${tenantId}`, token),
      api.get<PlatformPropertyRow[]>(`/platform-admin/tenants/${tenantId}/properties`, token),
    ])
      .then(([tenantResult, propertiesResult]) => {
        setTenant(tenantResult);
        setProperties(propertiesResult);
      })
      .catch((err) => setError(err instanceof ApiError && err.status === 404 ? t("admin.notFound") : t("admin.loadError")));
  }, [token, tenantId, t]);

  const dateFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium" });

  return (
    <div className="page">
      <Link to="/admin" className="back-link">
        ← {t("admin.backToList")}
      </Link>

      {error && <p className="error">{error}</p>}

      {tenant && (
        <>
          <div className="page-header">
            <div>
              <h1>{tenant.name}</h1>
              <p className="page-subtitle">
                <span className="mono">{tenant.slug}</span> ·{" "}
                {t(`countries.${tenant.countryCode}`, { defaultValue: tenant.countryCode })} ·{" "}
                {t("admin.since", { date: dateFormat.format(new Date(tenant.createdAt)) })}
              </p>
            </div>
            <StatusPill status={tenant.status} />
          </div>

          <div className="stat-grid">
            <StatCard label={t("dashboard.agents")} value={tenant.agents} icon={AgentsIcon} />
            <StatCard label={t("dashboard.properties")} value={tenant.properties} icon={PropertiesIcon} />
          </div>
        </>
      )}

      {properties && (
        <>
          <h2 className="section-title">{t("admin.propertiesTitle")}</h2>
          <table className="table">
            <thead>
              <tr>
                <th>{t("properties.columns.type")}</th>
                <th>{t("admin.columns.address")}</th>
                <th className="num">{t("properties.columns.area")}</th>
                <th className="num">{t("properties.columns.rooms")}</th>
                <th>{t("properties.columns.status")}</th>
                <th>{t("admin.columns.created")}</th>
              </tr>
            </thead>
            <tbody>
              {properties.map((p) => (
                <tr key={p.id}>
                  <td>{t(`propertyTypes.${p.type}`, { defaultValue: p.type })}</td>
                  <td>{formatAddress(p, t("admin.unit"))}</td>
                  <td className="num">{p.areaSqm} м²</td>
                  <td className="num">{p.rooms ?? "—"}</td>
                  <td>
                    <StatusPill status={p.status} />
                  </td>
                  <td>{dateFormat.format(new Date(p.createdAt))}</td>
                </tr>
              ))}
              {properties.length === 0 && (
                <tr>
                  <td colSpan={6}>{t("properties.empty")}</td>
                </tr>
              )}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}

function formatAddress(p: PlatformPropertyRow, unitLabel: string): string {
  const street = [p.street, p.houseNumber].filter(Boolean).join(", ");
  const unit = p.unitNumber ? `, ${unitLabel} ${p.unitNumber}` : "";
  return [p.city, street + unit].filter(Boolean).join(", ") || "—";
}
