import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { Agent } from "../api/types";
import { StatusPill } from "../components/StatusPill";

export function ProfilePage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [agent, setAgent] = useState<Agent | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    api
      .get<Agent>("/api/agents/me", token)
      .then(setAgent)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("profile.loadError")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  if (error) {
    return (
      <div className="page page-narrow">
        <p className="error">{error}</p>
      </div>
    );
  }

  if (!agent) {
    return <div className="page page-narrow" />;
  }

  return (
    <div className="page page-narrow">
      <h1>{t("profile.title")}</h1>
      <dl className="profile-fields">
        <dt>{t("profile.name")}</dt>
        <dd>
          {agent.firstName} {agent.lastName}
        </dd>
        <dt>{t("profile.email")}</dt>
        <dd>{agent.email}</dd>
        <dt>{t("profile.status")}</dt>
        <dd>
          <StatusPill status={agent.status} />
        </dd>
      </dl>
    </div>
  );
}
