import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { usePlatformAuth } from "../../auth/PlatformAuthContext";
import { api, ApiError } from "../../api/client";
import type { AgentAccount, PlatformPropertyRow, ResetPassword, TenantSummary } from "../../api/types";
import { StatusPill } from "../../components/StatusPill";
import { AgentsIcon, PropertiesIcon, StatCard } from "../../components/StatCard";

export function AdminTenantPage() {
  const { t, i18n } = useTranslation();
  const { tenantId } = useParams();
  const { token } = usePlatformAuth();
  const [tenant, setTenant] = useState<TenantSummary | null>(null);
  const [properties, setProperties] = useState<PlatformPropertyRow[] | null>(null);
  const [agents, setAgents] = useState<AgentAccount[] | null>(null);
  const [reset, setReset] = useState<ResetPassword | null>(null);
  const [agentError, setAgentError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadAgents = () => {
    if (!token || !tenantId) return;
    api.get<AgentAccount[]>(`/platform-admin/tenants/${tenantId}/agents`, token).then(setAgents).catch(() => setAgentError(t("admin.loadError")));
  };

  async function toggleAgent(agent: AgentAccount) {
    if (!token) return;
    setAgentError(null);
    try {
      const updated = await api.patch<AgentAccount>(`/platform-admin/tenants/${tenantId}/agents/${agent.id}/status`, { active: agent.status !== "ACTIVE" }, token);
      setAgents((list) => (list ?? []).map((a) => (a.id === updated.id ? updated : a)));
    } catch {
      setAgentError(t("admin.agents.actionFailed"));
    }
  }

  async function resetPassword(agent: AgentAccount) {
    if (!token || !window.confirm(t("admin.agents.resetConfirm", { email: agent.email }))) return;
    setAgentError(null);
    try {
      setReset(await api.post<ResetPassword>(`/platform-admin/tenants/${tenantId}/agents/${agent.id}/reset-password`, {}, token));
      loadAgents();
    } catch {
      setAgentError(t("admin.agents.actionFailed"));
    }
  }

  async function deleteAgent(agent: AgentAccount) {
    if (!token || !window.confirm(t("admin.agents.deleteConfirm", { email: agent.email }))) return;
    setAgentError(null);
    try {
      await api.del(`/platform-admin/tenants/${tenantId}/agents/${agent.id}`, token);
      setAgents((list) => (list ?? []).filter((a) => a.id !== agent.id));
      setTenant((tn) => (tn ? { ...tn, agents: tn.agents - 1 } : tn));
    } catch (err) {
      setAgentError(err instanceof ApiError && err.errors[0]?.code === "hasData" ? t("admin.agents.hasData") : t("admin.agents.actionFailed"));
    }
  }

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
    loadAgents();
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
            <a href="#agents" className="stat-link" onClick={(e) => { e.preventDefault(); document.getElementById("agents")?.scrollIntoView({ behavior: "smooth" }); }}>
              <StatCard label={t("dashboard.agents")} value={tenant.agents} icon={AgentsIcon} />
            </a>
            <a href="#properties" className="stat-link" onClick={(e) => { e.preventDefault(); document.getElementById("properties")?.scrollIntoView({ behavior: "smooth" }); }}>
              <StatCard label={t("dashboard.properties")} value={tenant.properties} icon={PropertiesIcon} />
            </a>
          </div>

          <h2 id="agents" className="section-title">
            {t("admin.agents.title")}
          </h2>
          <p className="hint">{t("admin.agents.hint")}</p>
          {reset && (
            <div className="invite-banner">
              <strong>{t("admin.agents.resetDone")}</strong>
              <p className="hint">{t("admin.createdHint")}</p>
              <code>
                {t("login.email")}: {reset.email}
                <br />
                {t("login.password")}: {reset.password}
              </code>
            </div>
          )}
          {agentError && <p className="error">{agentError}</p>}
          {agents && (
            <table className="table">
              <thead>
                <tr>
                  <th>{t("admin.columns.name")}</th>
                  <th>{t("login.email")}</th>
                  <th>{t("clients.columns.phone")}</th>
                  <th>{t("admin.columns.status")}</th>
                  <th>{t("admin.agents.login")}</th>
                  <th>{t("admin.columns.created")}</th>
                  <th className="actions-col">{t("admin.agents.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {agents.map((a) => (
                  <tr key={a.id} className={a.status === "DISABLED" ? "row-muted" : ""}>
                    <td className="strong">
                      {a.firstName} {a.lastName}
                    </td>
                    <td className="mono">{a.email}</td>
                    <td className="nowrap">{a.phone ?? "—"}</td>
                    <td>
                      <StatusPill status={a.status} />
                    </td>
                    <td>{a.loginStatus ? <StatusPill status={a.loginStatus} /> : "—"}</td>
                    <td className="nowrap">{dateFormat.format(new Date(a.createdAt))}</td>
                    <td className="actions-col">
                      <button type="button" className="icon-button" title={a.status === "ACTIVE" ? t("admin.agents.deactivate") : t("admin.agents.activate")} aria-label={a.status === "ACTIVE" ? t("admin.agents.deactivate") : t("admin.agents.activate")} onClick={() => toggleAgent(a)}>
                        {a.status === "ACTIVE" ? "⏸" : "▶︎"}
                      </button>
                      <button type="button" className="icon-button" title={t("admin.agents.resetPassword")} aria-label={t("admin.agents.resetPassword")} onClick={() => resetPassword(a)}>
                        🔑
                      </button>
                      <button type="button" className="icon-button danger" title={a.deletable ? t("admin.agents.delete") : t("admin.agents.hasData")} aria-label={t("admin.agents.delete")} disabled={!a.deletable} onClick={() => deleteAgent(a)}>
                        🗑
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}

      {properties && (
        <>
          <h2 id="properties" className="section-title">
            {t("admin.propertiesTitle")}
          </h2>
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
