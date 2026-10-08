import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api } from "../api/client";
import type { TaskView } from "../api/types";
import { fromLocalInput, nextRoundHour, propertyLabel } from "./showings/showingFormat";

/** Задачі агента: протерміновані · сьогодні · далі · виконані. */
export function TasksPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const [tasks, setTasks] = useState<TaskView[] | null>(null);
  const [showDone, setShowDone] = useState(false);
  const [adding, setAdding] = useState(false);

  const load = useCallback(() => {
    if (!token) return;
    api.get<TaskView[]>(`/api/tasks?includeDone=${showDone}`, token).then(setTasks).catch(() => setTasks([]));
  }, [token, showDone]);

  useEffect(() => load(), [load]);

  if (!token) return null;
  const now = Date.now();
  const endOfDay = new Date();
  endOfDay.setHours(23, 59, 59, 999);
  const open = (tasks ?? []).filter((x) => x.status === "OPEN");
  const groups: [string, TaskView[]][] = [
    ["overdue", open.filter((x) => new Date(x.dueAt).getTime() < now && new Date(x.dueAt).toDateString() !== new Date().toDateString())],
    ["today", open.filter((x) => new Date(x.dueAt).toDateString() === new Date().toDateString())],
    ["later", open.filter((x) => new Date(x.dueAt).getTime() > endOfDay.getTime())],
    ["done", (tasks ?? []).filter((x) => x.status !== "OPEN")],
  ];

  async function toggle(task: TaskView) {
    const updated = await api.post<TaskView>(`/api/tasks/${task.id}/${task.status === "OPEN" ? "complete" : "reopen"}`, {}, token!);
    setTasks((list) => (list ?? []).map((x) => (x.id === updated.id ? updated : x)));
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{t("tasks.title")}</h1>
          <p className="page-subtitle">{t("tasks.subtitle", { count: open.length })}</p>
        </div>
        <div className="form-actions">
          <label className="checkbox-row">
            <input type="checkbox" checked={showDone} onChange={(e) => setShowDone(e.target.checked)} /> {t("tasks.showDone")}
          </label>
          <button type="button" onClick={() => setAdding((v) => !v)}>
            + {t("tasks.new")}
          </button>
        </div>
      </div>

      {adding && <NewTaskForm token={token} onDone={() => { setAdding(false); load(); }} onCancel={() => setAdding(false)} />}

      {tasks && open.length === 0 && !showDone && (
        <div className="empty-state">
          <span className="empty-icon" aria-hidden="true">✅</span>
          <h2>{t("tasks.emptyTitle")}</h2>
          <p className="hint">{t("tasks.emptyHint")}</p>
        </div>
      )}

      {groups.map(([key, list]) =>
        list.length === 0 ? null : (
          <section key={key} className={`task-group task-group-${key}`}>
            <h2 className="section-title">
              {t(`tasks.groups.${key}`)} <span className="media-tab-count">{list.length}</span>
            </h2>
            <ul className="task-list">
              {list.map((task) => (
                <li key={task.id} className={`task-item kind-${task.kind.toLowerCase()} ${task.status !== "OPEN" ? "task-done" : ""}`}>
                  <input type="checkbox" checked={task.status === "DONE"} disabled={task.status === "CANCELLED" || task.kind === "SHOWING"} onChange={() => toggle(task)} aria-label={t("tasks.complete")} />
                  <div className="task-body">
                    <div className="task-title">
                      <span className={`task-kind kind-${task.kind.toLowerCase()}`}>{t(`tasks.kinds.${task.kind}`)}</span>
                      {task.showingId && task.property ? <Link to={`/properties/${task.property.id}/showings`}>{task.title}</Link> : task.title}
                    </div>
                    <div className="task-meta">
                      {new Intl.DateTimeFormat(i18n.language, { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(task.dueAt))}
                      {task.property && (
                        <>
                          {" · "}
                          <Link to={`/properties/${task.property.id}/sale`}>{propertyLabel(task.property, t("showings.property"))}</Link>
                        </>
                      )}
                      {task.client && (
                        <>
                          {" · "}
                          <Link to={`/clients/${task.client.id}`}>
                            {task.client.firstName} {task.client.lastName}
                          </Link>
                          {task.client.phone && <span className="hint"> {task.client.phone}</span>}
                        </>
                      )}
                      {task.note && <> · {task.note}</>}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ),
      )}
    </div>
  );
}

function NewTaskForm({ token, onDone, onCancel }: { token: string; onDone: () => void; onCancel: () => void }) {
  const { t } = useTranslation();
  const [title, setTitle] = useState("");
  const [when, setWhen] = useState(nextRoundHour(2));
  const [note, setNote] = useState("");

  async function submit(e: FormEvent) {
    e.preventDefault();
    await api.post("/api/tasks", { title, dueAt: fromLocalInput(when), note: note.trim() || null }, token);
    onDone();
  }

  return (
    <form className="panel form editor" onSubmit={submit}>
      <div className="form-grid">
        <label className="field field-wide">
          <span className="field-label">{t("tasks.fields.title")}</span>
          <input value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
        </label>
        <label className="field">
          <span className="field-label">{t("tasks.fields.due")}</span>
          <input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} required />
        </label>
        <label className="field">
          <span className="field-label">{t("tasks.fields.note")}</span>
          <input value={note} onChange={(e) => setNote(e.target.value)} />
        </label>
      </div>
      <div className="form-actions">
        <button type="submit">{t("tasks.save")}</button>
        <button type="button" className="button-secondary" onClick={onCancel}>
          {t("clients.cancel")}
        </button>
      </div>
    </form>
  );
}
