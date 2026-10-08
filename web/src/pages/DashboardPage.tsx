import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import type { TenantStats } from "../api/types";
import { AgentsIcon, PropertiesIcon, StatCard } from "../components/StatCard";

export function DashboardPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [stats, setStats] = useState<TenantStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    api
      .get<TenantStats>("/api/dashboard/stats", token)
      .then(setStats)
      .catch(() => setError(t("dashboard.loadError")));
  }, [token, t]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{t("dashboard.title")}</h1>
          <p className="page-subtitle">{t("dashboard.subtitle")}</p>
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      <div className="stat-grid">
        <Link to="/agents" className="stat-link">
          <StatCard label={t("dashboard.agents")} value={stats?.agents ?? null} icon={AgentsIcon} />
        </Link>
        <Link to="/properties" className="stat-link">
          <StatCard label={t("dashboard.properties")} value={stats?.properties ?? null} icon={PropertiesIcon} />
        </Link>
      </div>
    </div>
  );
}
