import { useEffect, useState, type FormEvent } from "react";
import { Trans, useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { Agent, InviteAgentRequest, InviteAgentResponse } from "../api/types";
import { StatusPill } from "../components/StatusPill";

export function AgentsPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [agents, setAgents] = useState<Agent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [lastInvite, setLastInvite] = useState<InviteAgentResponse | null>(null);

  async function loadAgents() {
    if (!token) return;
    try {
      setAgents(await api.get<Agent[]>("/api/agents", token));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("agents.loadError"));
    }
  }

  useEffect(() => {
    loadAgents();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    setError(null);
    const form = new FormData(event.currentTarget);
    const request: InviteAgentRequest = {
      email: String(form.get("email")),
      firstName: String(form.get("firstName")),
      lastName: String(form.get("lastName")),
    };
    try {
      const response = await api.post<InviteAgentResponse>("/api/agents/invite", request, token);
      setLastInvite(response);
      setShowForm(false);
      loadAgents();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("agents.inviteError"));
    }
  }

  async function toggleStatus(agent: Agent) {
    if (!token) return;
    const nextStatus = agent.status === "DISABLED" ? "ACTIVE" : "DISABLED";
    try {
      await api.patch(`/api/agents/${agent.id}/status`, { status: nextStatus }, token);
      loadAgents();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("agents.statusError"));
    }
  }

  const inviteLink = lastInvite ? `${window.location.origin}/accept-invite?token=${lastInvite.inviteToken}` : null;

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("agents.title")}</h1>
        <button onClick={() => setShowForm((v) => !v)}>
          {showForm ? t("agents.cancelButton") : t("agents.inviteButton")}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {inviteLink && (
        <div className="invite-banner">
          <p>
            <Trans i18nKey="agents.inviteBanner" values={{ email: lastInvite!.email }} components={{ bold: <strong /> }} />
          </p>
          <code className="mono">{inviteLink}</code>
        </div>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} className="form form-inline">
          <label>
            {t("agents.form.firstName")}
            <input name="firstName" required />
          </label>
          <label>
            {t("agents.form.lastName")}
            <input name="lastName" required />
          </label>
          <label>
            {t("agents.form.email")}
            <input name="email" type="email" required />
          </label>
          <button type="submit">{t("agents.form.submit")}</button>
        </form>
      )}

      <table className="table">
        <thead>
          <tr>
            <th>{t("agents.columns.name")}</th>
            <th>{t("agents.columns.email")}</th>
            <th>{t("agents.columns.status")}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {agents.map((a) => (
            <tr key={a.id}>
              <td>
                {a.firstName} {a.lastName}
              </td>
              <td>{a.email}</td>
              <td>
                <StatusPill status={a.status} />
              </td>
              <td>
                {a.status !== "INVITED" && (
                  <button className="link-button" onClick={() => toggleStatus(a)}>
                    {a.status === "DISABLED" ? t("agents.activate") : t("agents.deactivate")}
                  </button>
                )}
              </td>
            </tr>
          ))}
          {agents.length === 0 && (
            <tr>
              <td colSpan={4}>{t("agents.empty")}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
