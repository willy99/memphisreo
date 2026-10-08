import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { Client, ClientForm, ClientSource } from "../api/types";
import { Field } from "../components/form/Field";

const SOURCES: ClientSource[] = ["WEBSITE_INQUIRY", "REFERRAL", "ADVERTISEMENT", "WALK_IN", "OTHER"];
const EMPTY: ClientForm = { firstName: "", lastName: "", email: "", phone: "", source: "OTHER", notes: "" };

/** Контакти агенції — покупці й продавці. Роль контакту визначає угода, не сам запис. */
export function ClientsPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [clients, setClients] = useState<Client[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<Client | "new" | null>(null);

  async function load() {
    if (!token) return;
    try {
      setClients(await api.get<Client[]>("/api/clients", token));
    } catch {
      setError(t("clients.loadError"));
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (clients ?? []).filter((c) => !q || [c.firstName, c.lastName, c.email, c.phone, c.notes].some((x) => x?.toLowerCase().includes(q)));
  }, [clients, query]);

  const dateFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium" });

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{t("clients.title")}</h1>
          {clients && <p className="page-subtitle">{t("clients.total", { count: clients.length })}</p>}
        </div>
        <button type="button" onClick={() => setEditing(editing === "new" ? null : "new")}>
          {editing === "new" ? t("clients.cancel") : `+ ${t("clients.newButton")}`}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {editing && token && (
        <ClientEditor
          token={token}
          client={editing === "new" ? null : editing}
          onDone={() => {
            setEditing(null);
            load();
          }}
          onCancel={() => setEditing(null)}
        />
      )}

      {clients && clients.length > 0 && (
        <div className="list-toolbar">
          <input type="search" className="list-search" placeholder={t("clients.searchPlaceholder")} value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      )}

      {clients && clients.length === 0 && !editing && (
        <div className="empty-state">
          <span className="empty-icon" aria-hidden="true">
            👥
          </span>
          <h2>{t("clients.emptyTitle")}</h2>
          <p className="hint">{t("clients.emptyHint")}</p>
          <button type="button" onClick={() => setEditing("new")}>
            + {t("clients.newButton")}
          </button>
        </div>
      )}

      {visible.length > 0 && (
        <table className="table table-clickable">
          <thead>
            <tr>
              <th>{t("clients.columns.name")}</th>
              <th>{t("clients.columns.phone")}</th>
              <th>{t("clients.columns.email")}</th>
              <th>{t("clients.columns.source")}</th>
              <th>{t("clients.columns.notes")}</th>
              <th>{t("clients.columns.created")}</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((c) => (
              <tr key={c.id} onClick={() => navigate(`/clients/${c.id}`)}>
                <td className="strong">
                  {c.firstName} {c.lastName}
                </td>
                <td className="nowrap">{c.phone ?? "—"}</td>
                <td>{c.email ?? "—"}</td>
                <td>{t(`clients.sources.${c.source}`)}</td>
                <td className="notes-cell">{c.notes ?? ""}</td>
                <td className="nowrap">{dateFormat.format(new Date(c.createdAt))}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {clients && clients.length > 0 && visible.length === 0 && <p className="hint">{t("clients.nothingFound")}</p>}
    </div>
  );
}

function ClientEditor({ token, client, onDone, onCancel }: { token: string; client: Client | null; onDone: () => void; onCancel: () => void }) {
  const { t } = useTranslation();
  const [form, setForm] = useState<ClientForm>(
    client ? { firstName: client.firstName, lastName: client.lastName, email: client.email ?? "", phone: client.phone ?? "", source: client.source, notes: client.notes ?? "" } : EMPTY,
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const set = <K extends keyof ClientForm>(key: K) => (value: ClientForm[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => {
      const copy = { ...e };
      delete copy[key];
      return copy;
    });
  };
  const errorFor = (field: string) => (errors[field] ? t(`clients.errors.${errors[field]}`, { defaultValue: t("clients.errors.invalid") }) : null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setFailure(null);
    const payload: ClientForm = { ...form, email: form.email?.trim() || null, phone: form.phone?.trim() || null, notes: form.notes?.trim() || null };
    try {
      if (client) await api.put(`/api/clients/${client.id}`, payload, token);
      else await api.post("/api/clients", payload, token);
      onDone();
    } catch (err) {
      if (err instanceof ApiError && err.errors.length) {
        setErrors(Object.fromEntries(err.errors.map((e) => [e.field, e.code])));
      } else {
        setFailure(t("clients.saveError"));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} className="form panel editor">
      <h2>{client ? t("clients.editTitle") : t("clients.newTitle")}</h2>
      <div className="form-grid">
        <Field label={t("clients.fields.firstName")} required error={errorFor("firstName")}>
          {(id) => <input id={id} value={form.firstName} onChange={(e) => set("firstName")(e.target.value)} autoFocus />}
        </Field>
        <Field label={t("clients.fields.lastName")} required error={errorFor("lastName")}>
          {(id) => <input id={id} value={form.lastName} onChange={(e) => set("lastName")(e.target.value)} />}
        </Field>
        <Field label={t("clients.fields.phone")} error={errorFor("phone")} hint={t("clients.fields.contactHint")}>
          {(id) => <input id={id} type="tel" inputMode="tel" value={form.phone ?? ""} onChange={(e) => set("phone")(e.target.value)} placeholder="+380 67 000 00 00" />}
        </Field>
        <Field label={t("clients.fields.email")} error={errorFor("email")}>
          {(id) => <input id={id} type="email" value={form.email ?? ""} onChange={(e) => set("email")(e.target.value)} />}
        </Field>
        <Field label={t("clients.fields.source")}>
          {(id) => (
            <select id={id} value={form.source} onChange={(e) => set("source")(e.target.value as ClientSource)}>
              {SOURCES.map((s) => (
                <option key={s} value={s}>
                  {t(`clients.sources.${s}`)}
                </option>
              ))}
            </select>
          )}
        </Field>
      </div>
      <Field label={t("clients.fields.notes")} hint={t("clients.fields.notesHint")} wide>
        {(id) => <textarea id={id} rows={4} value={form.notes ?? ""} onChange={(e) => set("notes")(e.target.value)} />}
      </Field>
      {failure && <p className="error">{failure}</p>}
      <div className="form-actions">
        <button type="submit" disabled={submitting}>
          {submitting ? t("clients.saving") : t("clients.save")}
        </button>
        <button type="button" className="button-secondary" onClick={onCancel}>
          {t("clients.cancel")}
        </button>
      </div>
    </form>
  );
}
