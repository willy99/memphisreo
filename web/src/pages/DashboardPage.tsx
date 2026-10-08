import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import type { TenantStats, TodaySummary } from "../api/types";
import { ShowingCard } from "./showings/ShowingCard";
import { propertyLabel } from "./showings/showingFormat";

/** Головна: "мій день" (покази, задачі, заявки) + великі кнопки розділів. */
export function DashboardPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const [stats, setStats] = useState<TenantStats | null>(null);
  const [today, setToday] = useState<TodaySummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone || "Europe/Kyiv";
    api.get<TenantStats>("/api/dashboard/stats", token).then(setStats).catch(() => setError(t("dashboard.loadError")));
    api.get<TodaySummary>(`/api/dashboard/today?zone=${encodeURIComponent(zone)}`, token).then(setToday).catch(() => setError(t("dashboard.loadError")));
  }, [token, t]);

  if (!token) return null;
  const tiles = [
    { to: "/properties", icon: "🏠", label: t("nav.properties"), value: stats?.properties },
    { to: "/calendar", icon: "📅", label: t("nav.calendar"), value: today?.showingsToday, sub: t("dashboard.showingsToday") },
    { to: "/tasks", icon: "✅", label: t("nav.tasks"), value: today ? today.tasksToday + today.overdueTasks : undefined, sub: today?.overdueTasks ? t("dashboard.overdue", { count: today.overdueTasks }) : t("dashboard.tasksToday") },
    { to: "/clients", icon: "👥", label: t("nav.clients"), value: today?.newInquiries, sub: t("dashboard.newInquiries") },
  ];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{t("dashboard.title")}</h1>
          <p className="page-subtitle">{new Intl.DateTimeFormat(i18n.language, { weekday: "long", day: "numeric", month: "long" }).format(new Date())}</p>
        </div>
      </div>
      {error && <p className="error">{error}</p>}

      <div className="tile-grid">
        {tiles.map((tile) => (
          <Link key={tile.to} to={tile.to} className="tile">
            <span className="tile-icon" aria-hidden="true">{tile.icon}</span>
            <span className="tile-value">{tile.value ?? "—"}</span>
            <span className="tile-label">{tile.label}</span>
            {tile.sub && <span className="tile-sub">{tile.sub}</span>}
          </Link>
        ))}
      </div>

      {today && (
        <div className="today-grid">
          <section>
            <h2 className="section-title">{t("dashboard.todayShowings")}</h2>
            {today.nextShowings.length === 0 && <p className="hint">{t("dashboard.noShowings")}</p>}
            <div className="showing-list">
              {today.nextShowings.map((s) => (
                <ShowingCard key={s.id} token={token} showing={s} compact onChanged={(v) => setToday({ ...today, nextShowings: today.nextShowings.map((x) => (x.id === v.id ? v : x)) })} />
              ))}
            </div>
          </section>
          <section>
            <h2 className="section-title">{t("dashboard.todayTasks")}</h2>
            {today.dueTasks.length === 0 && today.overdueTasks === 0 && <p className="hint">{t("dashboard.noTasks")}</p>}
            {today.overdueTasks > 0 && (
              <p className="error">
                <Link to="/tasks">{t("dashboard.overdue", { count: today.overdueTasks })}</Link>
              </p>
            )}
            <ul className="task-list">
              {today.dueTasks.map((task) => (
                <li key={task.id} className={`task-item kind-${task.kind.toLowerCase()}`}>
                  <div className="task-body">
                    <div className="task-title">
                      <span className={`task-kind kind-${task.kind.toLowerCase()}`}>{t(`tasks.kinds.${task.kind}`)}</span>
                      {task.title}
                    </div>
                    <div className="task-meta">
                      {new Intl.DateTimeFormat(i18n.language, { hour: "2-digit", minute: "2-digit" }).format(new Date(task.dueAt))}
                      {task.property && <> · {propertyLabel(task.property, t("showings.property"))}</>}
                      {task.client && <> · {task.client.firstName} {task.client.lastName}</>}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
            {today.dueTasks.length > 0 && (
              <Link to="/tasks" className="link-button">
                {t("dashboard.allTasks")} →
              </Link>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
