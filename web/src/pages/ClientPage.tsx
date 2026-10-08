import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { Client, ClientDetails, ClientForm, ClientRequirement, ClientSource, RequirementForm, TimelineEntry } from "../api/types";
import { ChipGroup, Field, NumberInput, ToggleChips } from "../components/form/Field";
import { StatusPill } from "../components/StatusPill";
import { Timeline } from "../components/Timeline";
import { CURRENCIES, FEATURE_GROUPS, PROPERTY_TYPES, SQM_PER_SOTKA } from "./properties/propertyFields";

const SOURCES: ClientSource[] = ["WEBSITE_INQUIRY", "REFERRAL", "ADVERTISEMENT", "WALK_IN", "OTHER"];
const NOTE_KINDS = ["CALL", "MESSAGE", "MEETING", "NOTE"] as const;
const MUST_HAVE = ["SHELTER", "BACKUP_POWER", "UNDERGROUND_PARKING", "GARAGE", "BALCONY", "FURNISHED", "AIR_CONDITIONING", "SEPARATE_ENTRANCE", ...FEATURE_GROUPS.utilities] as const;

/** Картка клієнта: профіль, запит, підібрані об'єкти, об'єкти у власності, журнал контактів. */
export function ClientPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { clientId } = useParams();
  const [details, setDetails] = useState<ClientDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!token || !clientId) return;
    try {
      setDetails(await api.get<ClientDetails>(`/api/clients/${clientId}/details`, token));
    } catch (err) {
      setError(err instanceof ApiError && err.status === 404 ? t("clientPage.notFound") : t("clients.loadError"));
    }
  }, [token, clientId, t]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3000);
    return () => window.clearTimeout(timer);
  }, [toast]);

  if (!token || !clientId) return null;
  if (error) {
    return (
      <div className="page">
        <p className="error">{error}</p>
      </div>
    );
  }
  if (!details) return <div className="page" />;

  const c = details.client;
  return (
    <div className="page">
      <Link to="/clients" className="back-link">
        ← {t("clientPage.backToList")}
      </Link>
      <div className="page-header">
        <div>
          <h1>
            {c.firstName} {c.lastName}
          </h1>
          <p className="page-subtitle">
            {[c.phone, c.email, t(`clients.sources.${c.source}`)].filter(Boolean).join(" · ")}
          </p>
        </div>
      </div>

      <div className="sale-layout">
        <div className="sale-main">
          <RequirementCard token={token} clientId={clientId} requirement={details.requirement} onSaved={() => { load(); setToast(t("clientPage.requirementSaved")); }} />

          <section className="panel">
            <h2>{t("clientPage.matchesTitle", { count: details.matches.length })}</h2>
            {!details.requirement && <p className="hint">{t("clientPage.noRequirement")}</p>}
            {details.requirement && details.matches.length === 0 && <p className="hint">{t("clientPage.noMatches")}</p>}
            {details.matches.length > 0 && (
              <ul className="match-list">
                {details.matches.map((m) => (
                  <li key={m.property.id}>
                    <Link to={`/properties/${m.property.id}/sale`} className="match-row">
                      {m.property.coverThumbUrl ? <img src={m.property.coverThumbUrl} alt="" /> : <span className="match-noimg" />}
                      <span className="match-body">
                        <strong>{m.property.title || t(`propertyTypes.${m.property.type}`)}</strong>
                        <span className="hint">
                          {[m.property.district, m.property.street, m.property.rooms ? t("properties.roomsShort", { count: m.property.rooms }) : null, m.property.areaSqm ? `${m.property.areaSqm} м²` : m.property.landAreaSqm ? `${+(m.property.landAreaSqm / SQM_PER_SOTKA).toFixed(1)} ${t("propertyForm.units.sotka")}` : null].filter(Boolean).join(" · ")}
                        </span>
                        <span className="match-criteria">{m.matched.map((k) => t(`clientPage.criteria.${k}`)).join(" · ")}</span>
                      </span>
                      <span className="match-price">{m.property.price != null && m.property.currency ? new Intl.NumberFormat("uk-UA", { style: "currency", currency: m.property.currency, maximumFractionDigits: 0 }).format(m.property.price) : "—"}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <JournalCard token={token} clientId={clientId} entries={details.timeline} onChanged={(entries) => setDetails({ ...details, timeline: entries })} />
        </div>

        <aside className="sale-side">
          <ProfileCard token={token} client={c} onSaved={(client) => { setDetails({ ...details, client }); setToast(t("sale.saved")); }} />
          {details.owned.length > 0 && (
            <section className="panel">
              <h2>{t("clientPage.ownedTitle")}</h2>
              <ul className="agent-list">
                {details.owned.map((o) => (
                  <li key={o.id}>
                    <Link to={`/properties/${o.id}/sale`}>{o.title || o.id.slice(0, 8)}</Link> <StatusPill status={o.status} />
                  </li>
                ))}
              </ul>
            </section>
          )}
        </aside>
      </div>
      {toast && (
        <div className="toast" role="status">
          {toast}
        </div>
      )}
    </div>
  );
}

function ProfileCard({ token, client, onSaved }: { token: string; client: Client; onSaved: (c: Client) => void }) {
  const { t } = useTranslation();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<ClientForm>({ firstName: client.firstName, lastName: client.lastName, email: client.email ?? "", phone: client.phone ?? "", source: client.source, notes: client.notes ?? "" });
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      onSaved(await api.put<Client>(`/api/clients/${client.id}`, { ...form, email: form.email?.trim() || null, phone: form.phone?.trim() || null, notes: form.notes?.trim() || null }, token));
      setEditing(false);
    } catch (err) {
      if (err instanceof ApiError) setErrors(Object.fromEntries(err.errors.map((x) => [x.field, x.code])));
    }
  }

  if (!editing) {
    return (
      <section className="panel">
        <div className="status-card-head">
          <h2>{t("clientPage.profile")}</h2>
          <button type="button" className="link-button" onClick={() => setEditing(true)}>
            {t("sale.editAgents")}
          </button>
        </div>
        <dl className="profile-fields">
          <dt>{t("clients.fields.phone")}</dt>
          <dd>{client.phone ? <a href={`tel:${client.phone.replace(/\s/g, "")}`}>{client.phone}</a> : "—"}</dd>
          <dt>{t("clients.fields.email")}</dt>
          <dd>{client.email ?? "—"}</dd>
          <dt>{t("clients.fields.source")}</dt>
          <dd>{t(`clients.sources.${client.source}`)}</dd>
          {client.notes && (
            <>
              <dt>{t("clients.fields.notes")}</dt>
              <dd className="notes-cell">{client.notes}</dd>
            </>
          )}
        </dl>
      </section>
    );
  }

  const err = (f: string) => (errors[f] ? t(`clients.errors.${errors[f]}`, { defaultValue: t("clients.errors.invalid") }) : null);
  return (
    <form className="panel editor form" onSubmit={submit}>
      <h2>{t("clientPage.profile")}</h2>
      <Field label={t("clients.fields.firstName")} required error={err("firstName")}>
        {(id) => <input id={id} value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />}
      </Field>
      <Field label={t("clients.fields.lastName")} required error={err("lastName")}>
        {(id) => <input id={id} value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />}
      </Field>
      <Field label={t("clients.fields.phone")} error={err("phone")}>
        {(id) => <input id={id} type="tel" value={form.phone ?? ""} onChange={(e) => setForm({ ...form, phone: e.target.value })} />}
      </Field>
      <Field label={t("clients.fields.email")} error={err("email")}>
        {(id) => <input id={id} type="email" value={form.email ?? ""} onChange={(e) => setForm({ ...form, email: e.target.value })} />}
      </Field>
      <Field label={t("clients.fields.source")}>
        {(id) => (
          <select id={id} value={form.source} onChange={(e) => setForm({ ...form, source: e.target.value as ClientSource })}>
            {SOURCES.map((s) => (
              <option key={s} value={s}>
                {t(`clients.sources.${s}`)}
              </option>
            ))}
          </select>
        )}
      </Field>
      <Field label={t("clients.fields.notes")}>
        {(id) => <textarea id={id} rows={3} value={form.notes ?? ""} onChange={(e) => setForm({ ...form, notes: e.target.value })} />}
      </Field>
      <div className="form-actions">
        <button type="submit">{t("clients.save")}</button>
        <button type="button" className="button-secondary" onClick={() => setEditing(false)}>
          {t("clients.cancel")}
        </button>
      </div>
    </form>
  );
}

function RequirementCard({ token, clientId, requirement, onSaved }: { token: string; clientId: string; requirement: ClientRequirement | null; onSaved: () => void }) {
  const { t } = useTranslation();
  const r = requirement;
  const [type, setType] = useState(r?.propertyType ?? "");
  const [roomsMin, setRoomsMin] = useState(r?.roomsMin != null ? String(r.roomsMin) : "");
  const [roomsMax, setRoomsMax] = useState(r?.roomsMax != null ? String(r.roomsMax) : "");
  const [priceMin, setPriceMin] = useState(r?.priceMin != null ? String(r.priceMin) : "");
  const [priceMax, setPriceMax] = useState(r?.priceMax != null ? String(r.priceMax) : "");
  const [currency, setCurrency] = useState(r?.currency ?? "USD");
  const [areaMin, setAreaMin] = useState(r?.areaMin != null ? String(r.areaMin) : "");
  const [districts, setDistricts] = useState((r?.districts ?? []).join(", "));
  const [market, setMarket] = useState(r?.market ?? "");
  const [mustHave, setMustHave] = useState<string[]>(r?.mustHave ?? []);
  const [notes, setNotes] = useState(r?.notes ?? "");
  const [active, setActive] = useState(r?.active ?? true);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const num = (s: string) => (s.trim() === "" ? null : Number(s.replace(/\s/g, "").replace(",", ".")));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setErrors({});
    const form: RequirementForm = {
      propertyType: (type || null) as RequirementForm["propertyType"],
      roomsMin: num(roomsMin), roomsMax: num(roomsMax), priceMin: num(priceMin), priceMax: num(priceMax), currency,
      areaMin: num(areaMin), districts: districts.split(",").map((d) => d.trim()).filter(Boolean), market: market || null,
      mustHave, notes: notes.trim() || null, active,
    };
    try {
      await api.put(`/api/clients/${clientId}/requirement`, form, token);
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setErrors(Object.fromEntries(err.errors.map((x) => [x.field, x.code])));
    } finally {
      setSaving(false);
    }
  }
  const err = (f: string) => (errors[f] ? t(`propertyForm.errors.${errors[f]}`, { defaultValue: t("propertyForm.errors.invalid") }) : null);

  return (
    <form className="panel editor form" onSubmit={submit}>
      <div className="status-card-head">
        <h2>{t("clientPage.requirementTitle")}</h2>
        <label className="checkbox-row">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> {t("clientPage.requirementActive")}
        </label>
      </div>
      <p className="hint">{t("clientPage.requirementHint")}</p>
      <Field label={t("propertyForm.fields.type")} wide>
        {() => <ChipGroup label={t("propertyForm.fields.type")} value={type} options={PROPERTY_TYPES.map((p) => ({ value: p, label: t(`propertyTypes.${p}`) }))} onChange={setType} />}
      </Field>
      <div className="form-grid">
        <Field label={t("clientPage.fields.roomsMin")} error={err("roomsMin")}>
          {(id) => <NumberInput id={id} value={roomsMin} onChange={setRoomsMin} />}
        </Field>
        <Field label={t("clientPage.fields.roomsMax")} error={err("roomsMax")}>
          {(id) => <NumberInput id={id} value={roomsMax} onChange={setRoomsMax} />}
        </Field>
        <Field label={t("clientPage.fields.priceMin")} error={err("priceMin")}>
          {(id) => <NumberInput id={id} value={priceMin} onChange={setPriceMin} unit={currency} decimal />}
        </Field>
        <Field label={t("clientPage.fields.priceMax")} error={err("priceMax")}>
          {(id) => <NumberInput id={id} value={priceMax} onChange={setPriceMax} unit={currency} decimal />}
        </Field>
        <Field label={t("propertyForm.fields.currency")}>
          {() => <ChipGroup label={t("propertyForm.fields.currency")} allowClear={false} value={currency} options={CURRENCIES.map((cu) => ({ value: cu, label: cu }))} onChange={setCurrency} />}
        </Field>
        <Field label={t("clientPage.fields.areaMin")} hint={t("clientPage.fields.areaHint")}>
          {(id) => <NumberInput id={id} value={areaMin} onChange={setAreaMin} unit="м²" decimal />}
        </Field>
      </div>
      <Field label={t("clientPage.fields.districts")} hint={t("clientPage.fields.districtsHint")} wide>
        {(id) => <input id={id} value={districts} onChange={(e) => setDistricts(e.target.value)} placeholder="Аркадія, Центр, Фонтанка" />}
      </Field>
      <Field label={t("propertyForm.fields.market")} wide>
        {() => (
          <ChipGroup label={t("propertyForm.fields.market")} value={market} options={[{ value: "SECONDARY", label: t("propertyForm.options.market.SECONDARY") }, { value: "NEW_BUILD", label: t("propertyForm.options.market.NEW_BUILD") }]} onChange={setMarket} />
        )}
      </Field>
      <Field label={t("clientPage.fields.mustHave")} wide>
        {() => <ToggleChips options={MUST_HAVE.map((f) => ({ value: f, label: t(`propertyForm.features.${f}`) }))} selected={mustHave} onToggle={(f) => setMustHave(mustHave.includes(f) ? mustHave.filter((x) => x !== f) : [...mustHave, f])} />}
      </Field>
      <Field label={t("clients.fields.notes")} wide>
        {(id) => <textarea id={id} rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />}
      </Field>
      <div className="form-actions">
        <button type="submit" disabled={saving}>
          {saving ? t("sale.saving") : t("clientPage.saveRequirement")}
        </button>
      </div>
    </form>
  );
}

function JournalCard({ token, clientId, entries, onChanged }: { token: string; clientId: string; entries: TimelineEntry[]; onChanged: (e: TimelineEntry[]) => void }) {
  const { t } = useTranslation();
  const [kind, setKind] = useState<string>("CALL");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!note.trim()) return;
    setSaving(true);
    try {
      onChanged(await api.post<TimelineEntry[]>(`/api/clients/${clientId}/notes`, { kind, note }, token));
      setNote("");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel">
      <h2>{t("clientPage.journalTitle")}</h2>
      <form className="journal-form" onSubmit={submit}>
        <ChipGroup label={t("clientPage.noteKind")} allowClear={false} value={kind} options={NOTE_KINDS.map((k) => ({ value: k, label: t(`clientPage.noteKinds.${k}`) }))} onChange={setKind} />
        <div className="journal-row">
          <input value={note} placeholder={t("clientPage.notePlaceholder")} onChange={(e) => setNote(e.target.value)} />
          <button type="submit" disabled={saving || !note.trim()}>
            {t("clientPage.addNote")}
          </button>
        </div>
      </form>
      <Timeline entries={entries} emptyText={t("clientPage.noEvents")} />
    </section>
  );
}
