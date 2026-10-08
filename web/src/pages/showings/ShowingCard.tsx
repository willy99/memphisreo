import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, ApiError } from "../../api/client";
import type { FeedbackForm, NextStep, Objection, ShowingView } from "../../api/types";
import { ChipGroup } from "../../components/form/Field";
import { StatusStrip, showingProgress, showingSteps } from "../../components/StatusStrip";
import { dayLabel, fromLocalInput, propertyAddress, propertyLabel, timeRange, toLocalInput } from "./showingFormat";

const OBJECTIONS: Objection[] = ["PRICE", "CONDITION", "LOCATION", "LAYOUT", "OTHER"];
const NEXT_STEPS: NextStep[] = ["REPEAT_SHOWING", "WAITING", "OFFER", "NOT_SUITABLE"];
const API = "http://localhost:8080";

interface ShowingCardProps {
  token: string;
  showing: ShowingView;
  onChanged: (s: ShowingView) => void;
  /** Де показуємо: на об'єкті — ховаємо об'єкт; на клієнті — ховаємо клієнта. */
  hide?: "property" | "client";
  compact?: boolean;
}

/** Картка показу: стрічка статусів, хто/де/коли, дії, фідбек. */
export function ShowingCard({ token, showing, onChanged, hide, compact }: ShowingCardProps) {
  const { t, i18n } = useTranslation();
  const [mode, setMode] = useState<"view" | "feedback" | "reschedule">("view");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const s = showing;
  const progress = showingProgress(s.status, !!s.feedbackAt);
  const active = s.status === "SCHEDULED" || s.status === "CONFIRMED";
  const past = new Date(s.scheduledAt).getTime() < Date.now();

  async function act(path: string, body: unknown = {}) {
    setBusy(true);
    setError(null);
    try {
      onChanged(await api.post<ShowingView>(`/api/showings/${s.id}/${path}`, body, token));
      setMode("view");
    } catch (err) {
      setError(err instanceof ApiError && err.errors[0] ? t(`showings.errors.${err.errors[0].code}`, { defaultValue: err.message }) : t("showings.actionFailed"));
    } finally {
      setBusy(false);
    }
  }

  function cancel() {
    const reason = window.prompt(t("showings.cancelPrompt"));
    if (reason === null) return;
    void act("cancel", { reason });
  }

  function copyConfirmation() {
    const when = `${dayLabel(s.scheduledAt, i18n.language)}, ${timeRange(s.scheduledAt, s.durationMinutes, i18n.language)}`;
    const text = t("showings.confirmationText", { when, address: propertyAddress(s.property) || propertyLabel(s.property, t("showings.property")), agent: s.agent ? `${s.agent.firstName} ${s.agent.phone ?? ""}`.trim() : "" });
    navigator.clipboard.writeText(text).then(() => setError(null)).catch(() => window.prompt(t("sale.copyManually"), text));
  }

  return (
    <article className={`showing-card status-${s.status.toLowerCase()}${compact ? " compact" : ""}`}>
      <StatusStrip steps={showingSteps(t)} current={progress.current} terminated={progress.terminated} terminatedLabel={progress.terminated ? t(`showings.status.${s.status}`) : undefined} compact={compact} />
      <div className="showing-head">
        <div className="showing-when">
          <strong>{dayLabel(s.scheduledAt, i18n.language)}</strong>
          <span>{timeRange(s.scheduledAt, s.durationMinutes, i18n.language)}</span>
        </div>
        <div className="showing-who">
          {hide !== "property" && s.property && (
            <Link to={`/properties/${s.property.id}/showings`} className="showing-link">
              🏠 {propertyLabel(s.property, t("showings.property"))}
              <span className="hint"> · {propertyAddress(s.property)}</span>
            </Link>
          )}
          {hide !== "client" && s.clients.length > 0 && (
            <span>
              👤{" "}
              {s.clients.map((c, i) => (
                <span key={c.id}>
                  {i > 0 && ", "}
                  <Link to={`/clients/${c.id}`} className="showing-link">
                    {c.firstName} {c.lastName}
                  </Link>
                  {c.phone && <span className="hint"> {c.phone}</span>}
                </span>
              ))}
            </span>
          )}
          {s.agent && (
            <span className="hint">
              {t("showings.agent")}: {s.agent.firstName} {s.agent.lastName}
            </span>
          )}
        </div>
      </div>
      {s.notes && <p className="showing-notes">{s.notes}</p>}
      {s.property?.accessNotes && active && <p className="showing-access">🔑 {s.property.accessNotes}</p>}
      {s.cancelReason && <p className="hint">{t("showings.cancelledBecause", { reason: s.cancelReason })}</p>}

      {s.feedbackAt && (
        <div className="feedback-summary">
          <span className="interest" aria-label={t("showings.feedback.interest")}>
            {"★".repeat(s.interest ?? 0)}
            {"☆".repeat(5 - (s.interest ?? 0))}
          </span>
          {s.objection && <span className="chip chip-selected chip-small">{t(`showings.objections.${s.objection}`)}</span>}
          {s.nextStep && <span className="chip chip-small">{t(`showings.nextSteps.${s.nextStep}`)}</span>}
          {s.feedbackComment && <p>{s.feedbackComment}</p>}
        </div>
      )}

      {error && <p className="error">{error}</p>}

      {mode === "feedback" && <FeedbackPanel busy={busy} onSubmit={(f) => act("feedback", f)} onCancel={() => setMode("view")} />}
      {mode === "reschedule" && <ReschedulePanel showing={s} token={token} onDone={(v) => { onChanged(v); setMode("view"); }} onCancel={() => setMode("view")} />}

      {mode === "view" && (
        <div className="showing-actions">
          {s.status === "SCHEDULED" && (
            <button type="button" className="button-secondary" disabled={busy} onClick={() => act("confirm")}>
              ✓ {t("showings.actions.confirm")}
            </button>
          )}
          {active && (
            <button type="button" disabled={busy} onClick={() => act("complete", { noShow: false })}>
              {t("showings.actions.complete")}
            </button>
          )}
          {active && past && (
            <button type="button" className="button-secondary" disabled={busy} onClick={() => act("complete", { noShow: true })}>
              {t("showings.actions.noShow")}
            </button>
          )}
          {s.status === "COMPLETED" && !s.feedbackAt && (
            <button type="button" onClick={() => setMode("feedback")}>
              ★ {t("showings.actions.feedback")}
            </button>
          )}
          {active && (
            <>
              <button type="button" className="button-secondary" onClick={() => setMode("reschedule")}>
                {t("showings.actions.reschedule")}
              </button>
              <button type="button" className="link-button" onClick={copyConfirmation}>
                {t("showings.actions.copyConfirmation")}
              </button>
              <a className="link-button" href={`${API}/api/showings/${s.id}/invite.ics`} onClick={(e) => { e.preventDefault(); downloadIcs(token, s.id); }}>
                {t("showings.actions.ics")}
              </a>
              <button type="button" className="link-button danger-text" disabled={busy} onClick={cancel}>
                {t("showings.actions.cancel")}
              </button>
            </>
          )}
        </div>
      )}
    </article>
  );
}

async function downloadIcs(token: string, id: string) {
  const response = await fetch(`${API}/api/showings/${id}/invite.ics`, { headers: { Authorization: `Bearer ${token}` } });
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "showing.ics";
  a.click();
  URL.revokeObjectURL(url);
}

function FeedbackPanel({ busy, onSubmit, onCancel }: { busy: boolean; onSubmit: (f: FeedbackForm) => void; onCancel: () => void }) {
  const { t } = useTranslation();
  const [interest, setInterest] = useState(0);
  const [objection, setObjection] = useState<string>("");
  const [nextStep, setNextStep] = useState<string>("");
  const [comment, setComment] = useState("");

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!interest) return;
    onSubmit({ interest, objection: (objection || null) as Objection | null, comment: comment.trim() || null, nextStep: (nextStep || null) as NextStep | null });
  }

  return (
    <form className="feedback-panel" onSubmit={submit}>
      <div className="feedback-row">
        <span className="field-label">{t("showings.feedback.interest")}</span>
        <div className="stars" role="radiogroup" aria-label={t("showings.feedback.interest")}>
          {[1, 2, 3, 4, 5].map((n) => (
            <button key={n} type="button" role="radio" aria-checked={interest === n} className={`star${n <= interest ? " on" : ""}`} onClick={() => setInterest(n)} aria-label={String(n)}>
              ★
            </button>
          ))}
        </div>
      </div>
      <div className="feedback-row">
        <span className="field-label">{t("showings.feedback.objection")}</span>
        <ChipGroup label={t("showings.feedback.objection")} value={objection} options={OBJECTIONS.map((o) => ({ value: o, label: t(`showings.objections.${o}`) }))} onChange={setObjection} />
      </div>
      <div className="feedback-row">
        <span className="field-label">{t("showings.feedback.nextStep")}</span>
        <ChipGroup label={t("showings.feedback.nextStep")} value={nextStep} options={NEXT_STEPS.map((n) => ({ value: n, label: t(`showings.nextSteps.${n}`) }))} onChange={setNextStep} />
        <span className="hint">{t("showings.feedback.nextStepHint")}</span>
      </div>
      <textarea rows={2} placeholder={t("showings.feedback.comment")} value={comment} onChange={(e) => setComment(e.target.value)} />
      <div className="form-actions">
        <button type="submit" disabled={busy || !interest}>
          {t("showings.feedback.save")}
        </button>
        <button type="button" className="button-secondary" onClick={onCancel}>
          {t("clients.cancel")}
        </button>
      </div>
    </form>
  );
}

function ReschedulePanel({ showing, token, onDone, onCancel }: { showing: ShowingView; token: string; onDone: (s: ShowingView) => void; onCancel: () => void }) {
  const { t } = useTranslation();
  const [when, setWhen] = useState(toLocalInput(showing.scheduledAt));
  const [duration, setDuration] = useState(String(showing.durationMinutes));
  const [notes, setNotes] = useState(showing.notes ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent, ignoreAgentOverlap = false) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onDone(await api.put<ShowingView>(`/api/showings/${showing.id}`, { scheduledAt: fromLocalInput(when), durationMinutes: Number(duration), notes: notes.trim() || null, ignoreAgentOverlap }, token));
    } catch (err) {
      const code = err instanceof ApiError ? err.errors[0]?.code : undefined;
      if (code === "agentBusy" && window.confirm(t("showings.errors.agentBusyConfirm"))) {
        await submit(e, true);
        return;
      }
      setError(code ? t(`showings.errors.${code}`, { defaultValue: t("showings.actionFailed") }) : t("showings.actionFailed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="feedback-panel" onSubmit={(e) => submit(e)}>
      <div className="form-grid">
        <label className="field">
          <span className="field-label">{t("showings.form.when")}</span>
          <input type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} required />
        </label>
        <label className="field">
          <span className="field-label">{t("showings.form.duration")}</span>
          <select value={duration} onChange={(e) => setDuration(e.target.value)}>
            {[30, 45, 60, 90].map((m) => (
              <option key={m} value={m}>
                {m} {t("showings.form.minutes")}
              </option>
            ))}
          </select>
        </label>
      </div>
      <input placeholder={t("showings.form.notes")} value={notes} onChange={(e) => setNotes(e.target.value)} />
      {error && <p className="error">{error}</p>}
      <div className="form-actions">
        <button type="submit" disabled={busy}>
          {t("showings.actions.saveReschedule")}
        </button>
        <button type="button" className="button-secondary" onClick={onCancel}>
          {t("clients.cancel")}
        </button>
      </div>
    </form>
  );
}
