import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { api, ApiError } from "../../api/client";
import type { Agent, Client, PropertyCard, ShowingView } from "../../api/types";
import { fromLocalInput, nextRoundHour } from "./showingFormat";

interface Props {
  token: string;
  /** Один із двох зафіксований тим, звідки відкрили діалог. */
  propertyId?: string;
  clientId?: string;
  defaultAgentId?: string;
  onScheduled: (s: ShowingView) => void;
  onClose: () => void;
}

/** Діалог планування показу: другий учасник — з пошуку, дата, тривалість, агент, нотатка. */
export function ScheduleShowingDialog({ token, propertyId, clientId, defaultAgentId, onScheduled, onClose }: Props) {
  const { t } = useTranslation();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [properties, setProperties] = useState<PropertyCard[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [property, setProperty] = useState(propertyId ?? "");
  const [selectedClients, setSelectedClients] = useState<string[]>(clientId ? [clientId] : []);
  const [agent, setAgent] = useState(defaultAgentId ?? "");
  const [when, setWhen] = useState(nextRoundHour());
  const [duration, setDuration] = useState("45");
  const [notes, setNotes] = useState("");
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    dialogRef.current?.showModal();
    if (!propertyId) api.get<PropertyCard[]>("/api/properties", token).then((list) => setProperties(list.filter((p) => p.status === "ACTIVE"))).catch(() => undefined);
    api.get<Client[]>("/api/clients", token).then(setClients).catch(() => undefined);
    api.get<Agent[]>("/api/agents", token).then((list) => setAgents(list.filter((a) => a.status === "ACTIVE"))).catch(() => undefined);
  }, [token, propertyId]);

  const clientMatches = useMemo(() => {
    const q = query.trim().toLowerCase();
    return clients.filter((c) => !selectedClients.includes(c.id) && (!q || `${c.firstName} ${c.lastName} ${c.phone ?? ""}`.toLowerCase().includes(q))).slice(0, 6);
  }, [clients, query, selectedClients]);

  async function submit(e: FormEvent, ignoreAgentOverlap = false) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const view = await api.post<ShowingView>("/api/showings", {
        propertyId: property, clientIds: selectedClients, agentId: agent || null, scheduledAt: fromLocalInput(when),
        durationMinutes: Number(duration), notes: notes.trim() || null, ignoreAgentOverlap,
      }, token);
      onScheduled(view);
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
    <dialog ref={dialogRef} className="dialog" onCancel={onClose} onClick={(e) => e.target === dialogRef.current && onClose()}>
      <form className="dialog-body form" onSubmit={(e) => submit(e)}>
        <h2>{t("showings.schedule.title")}</h2>

        {!propertyId && (
          <label className="field">
            <span className="field-label">{t("showings.property")}</span>
            <select value={property} onChange={(e) => setProperty(e.target.value)} required>
              <option value="">—</option>
              {properties.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title || t(`propertyTypes.${p.type}`)} · {[p.street, p.houseNumber].filter(Boolean).join(", ")}
                </option>
              ))}
            </select>
          </label>
        )}

        {!clientId && (
          <div className="field">
            <span className="field-label">{t("showings.schedule.clients")}</span>
            {selectedClients.length > 0 && (
              <div className="chip-group">
                {selectedClients.map((id) => {
                  const c = clients.find((x) => x.id === id);
                  return (
                    <button key={id} type="button" className="chip chip-selected" onClick={() => setSelectedClients(selectedClients.filter((x) => x !== id))}>
                      {c ? `${c.firstName} ${c.lastName}` : id} ✕
                    </button>
                  );
                })}
              </div>
            )}
            <input placeholder={t("sale.sellerSearch")} value={query} onChange={(e) => setQuery(e.target.value)} />
            {query && (
              <ul className="picker-list">
                {clientMatches.map((c) => (
                  <li key={c.id}>
                    <button type="button" className="link-button" onClick={() => { setSelectedClients([...selectedClients, c.id]); setQuery(""); }}>
                      {c.firstName} {c.lastName} <span className="hint">{c.phone}</span>
                    </button>
                  </li>
                ))}
                {clientMatches.length === 0 && <li className="hint">{t("clients.nothingFound")}</li>}
              </ul>
            )}
          </div>
        )}

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
          <label className="field">
            <span className="field-label">{t("showings.agent")}</span>
            <select value={agent} onChange={(e) => setAgent(e.target.value)}>
              <option value="">{t("showings.schedule.me")}</option>
              {agents.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.firstName} {a.lastName}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          <span className="field-label">{t("showings.form.notes")}</span>
          <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t("showings.form.notesPlaceholder")} />
        </label>
        {error && <p className="error">{error}</p>}
        <div className="form-actions">
          <button type="submit" disabled={busy || !property || selectedClients.length === 0}>
            {busy ? t("sale.saving") : t("showings.schedule.submit")}
          </button>
          <button type="button" className="button-secondary" onClick={onClose}>
            {t("clients.cancel")}
          </button>
        </div>
      </form>
    </dialog>
  );
}
