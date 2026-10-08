import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import type { Agent, CalendarView, ShowingView, TaskView } from "../api/types";
import { ShowingCard } from "./showings/ShowingCard";
import { ScheduleShowingDialog } from "./showings/ScheduleShowingDialog";
import { propertyLabel, timeRange } from "./showings/showingFormat";

function startOfWeek(d: Date): Date {
  const date = new Date(d);
  const day = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - day);
  date.setHours(0, 0, 0, 0);
  return date;
}

/** Календар: тиждень по днях, мій / агент / уся агенція; клік по показу — картка; iCal-підписка. */
export function CalendarPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()));
  const [agents, setAgents] = useState<Agent[]>([]);
  const [me, setMe] = useState<Agent | null>(null);
  const [agentFilter, setAgentFilter] = useState<string>("me");
  const [data, setData] = useState<CalendarView | null>(null);
  const [selected, setSelected] = useState<ShowingView | null>(null);
  const [dialog, setDialog] = useState(false);
  const [subscription, setSubscription] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    api.get<Agent>("/api/agents/me", token).then(setMe).catch(() => undefined);
    api.get<Agent[]>("/api/agents", token).then((list) => setAgents(list.filter((a) => a.status === "ACTIVE"))).catch(() => undefined);
  }, [token]);

  const load = useCallback(() => {
    if (!token) return;
    const from = weekStart.toISOString();
    const to = new Date(weekStart.getTime() + 7 * 86400000).toISOString();
    const agentId = agentFilter === "all" ? "" : agentFilter === "me" ? (me?.id ?? "") : agentFilter;
    if (agentFilter === "me" && !me) return;
    api.get<CalendarView>(`/api/calendar?from=${from}&to=${to}${agentId ? `&agentId=${agentId}` : ""}`, token).then(setData).catch(() => setData({ showings: [], tasks: [] }));
  }, [token, weekStart, agentFilter, me]);

  useEffect(() => load(), [load]);

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => new Date(weekStart.getTime() + i * 86400000)), [weekStart]);
  const dayKey = (d: Date | string) => new Date(d).toDateString();

  async function subscribe() {
    if (!token) return;
    if (!window.confirm(t("calendar.subscribeConfirm"))) return;
    const result = await api.post<{ url: string }>("/api/calendar/subscription", {}, token);
    setSubscription(result.url);
    navigator.clipboard.writeText(result.url).catch(() => undefined);
  }

  if (!token) return null;
  const today = new Date().toDateString();
  const fmtDay = new Intl.DateTimeFormat(i18n.language, { weekday: "short", day: "numeric" });
  const fmtRange = new Intl.DateTimeFormat(i18n.language, { day: "numeric", month: "long" });

  return (
    <div className="page calendar-page">
      <div className="page-header">
        <div>
          <h1>{t("calendar.title")}</h1>
          <p className="page-subtitle">
            {fmtRange.format(days[0])} — {fmtRange.format(days[6])}
          </p>
        </div>
        <div className="calendar-controls">
          <div className="segmented">
            <button type="button" onClick={() => setWeekStart(new Date(weekStart.getTime() - 7 * 86400000))} aria-label={t("calendar.prevWeek")}>
              ←
            </button>
            <button type="button" onClick={() => setWeekStart(startOfWeek(new Date()))}>
              {t("calendar.today")}
            </button>
            <button type="button" onClick={() => setWeekStart(new Date(weekStart.getTime() + 7 * 86400000))} aria-label={t("calendar.nextWeek")}>
              →
            </button>
          </div>
          <select value={agentFilter} onChange={(e) => setAgentFilter(e.target.value)} aria-label={t("showings.agent")}>
            <option value="me">{t("calendar.mine")}</option>
            <option value="all">{t("calendar.agency")}</option>
            {agents.filter((a) => a.id !== me?.id).map((a) => (
              <option key={a.id} value={a.id}>
                {a.firstName} {a.lastName}
              </option>
            ))}
          </select>
          <button type="button" onClick={() => setDialog(true)}>
            + {t("showings.schedule.button")}
          </button>
        </div>
      </div>

      <div className="week-grid">
        {days.map((day) => {
          const key = dayKey(day);
          const showings = (data?.showings ?? []).filter((s) => dayKey(s.scheduledAt) === key);
          const tasks = (data?.tasks ?? []).filter((x) => dayKey(x.dueAt) === key);
          return (
            <section key={key} className={`week-day${key === today ? " is-today" : ""}`}>
              <h2 className="week-day-title">{fmtDay.format(day)}</h2>
              {showings.length === 0 && tasks.length === 0 && <p className="hint week-empty">—</p>}
              {showings.map((s) => (
                <button key={s.id} type="button" className={`cal-event status-${s.status.toLowerCase()}${selected?.id === s.id ? " selected" : ""}`} onClick={() => setSelected(s)}>
                  <span className="cal-time">{timeRange(s.scheduledAt, s.durationMinutes, i18n.language)}</span>
                  <span className="cal-title">{propertyLabel(s.property, t("showings.property"))}</span>
                  <span className="cal-sub">{s.clients.map((c) => c.firstName + " " + c.lastName).join(", ")}{agentFilter !== "me" && s.agent ? ` · ${s.agent.firstName}` : ""}</span>
                </button>
              ))}
              {tasks.map((task: TaskView) => (
                <Link key={task.id} to="/tasks" className={`cal-event cal-task${task.status !== "OPEN" ? " task-done" : ""}`}>
                  <span className="cal-time">{new Intl.DateTimeFormat(i18n.language, { hour: "2-digit", minute: "2-digit" }).format(new Date(task.dueAt))}</span>
                  <span className="cal-title">☐ {task.title}</span>
                </Link>
              ))}
            </section>
          );
        })}
      </div>

      {selected && (
        <div className="calendar-detail">
          <ShowingCard token={token} showing={selected} onChanged={(v) => { setSelected(v); load(); }} />
          <button type="button" className="link-button" onClick={() => setSelected(null)}>
            {t("propertyForm.media.close")}
          </button>
        </div>
      )}

      <div className="calendar-footer">
        <button type="button" className="button-secondary" onClick={subscribe}>
          📆 {t("calendar.subscribe")}
        </button>
        <span className="hint">{subscription ? t("calendar.subscribed", { url: subscription }) : t("calendar.subscribeHint")}</span>
      </div>

      {dialog && <ScheduleShowingDialog token={token} defaultAgentId={me?.id} onScheduled={() => { setDialog(false); load(); }} onClose={() => setDialog(false)} />}
    </div>
  );
}
