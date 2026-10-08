import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../auth/AuthContext";
import { api, ApiError } from "../../api/client";
import type { Agent, AgentView, Client, ListingStatus, MandateType, OwnerLink, SaleForm, SaleView } from "../../api/types";
import { ChipGroup, Field, NumberInput } from "../../components/form/Field";
import { StatusPill } from "../../components/StatusPill";
import { PropertyTabs } from "./PropertyTabs";
import { CURRENCIES } from "./propertyFields";

/** Вкладка "Продаж": статус і дії, ціна, власник, мандат, доступ, агенти, "Поділитись". */
export function PropertySalePage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const { propertyId } = useParams();
  const [sale, setSale] = useState<SaleView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!token || !propertyId) return;
    try {
      setSale(await api.get<SaleView>(`/api/properties/${propertyId}/sale`, token));
    } catch (err) {
      setError(err instanceof ApiError && err.status === 404 ? t("sale.notFound") : t("sale.loadError"));
    }
  }, [token, propertyId, t]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  if (!token || !propertyId) return null;

  return (
    <div className="page">
      <Link to="/properties" className="back-link">
        ← {t("propertyForm.backToList")}
      </Link>
      <PropertyTabs propertyId={propertyId} />
      {error && <p className="error">{error}</p>}
      {sale && (
        <div className="sale-layout">
          <div className="sale-main">
            <SaleForm_ token={token} propertyId={propertyId} sale={sale} onSaved={setSale} notify={setToast} />
            <AgentsCard token={token} propertyId={propertyId} agents={sale.agents} onSaved={(agents) => setSale({ ...sale, agents })} notify={setToast} />
          </div>
          <aside className="sale-side">
            <StatusCard token={token} propertyId={propertyId} sale={sale} onChanged={setSale} notify={setToast} />
            {sale.publicUrl && <ShareCard sale={sale} notify={setToast} />}
            {sale.priceHistory.length > 1 && (
              <div className="panel">
                <h2>{t("sale.priceHistory")}</h2>
                <ul className="price-history">
                  {sale.priceHistory.map((p) => (
                    <li key={p.changedAt}>
                      <span>{new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium" }).format(new Date(p.changedAt))}</span>
                      <strong>{new Intl.NumberFormat(i18n.language, { style: "currency", currency: p.currency, maximumFractionDigits: 0 }).format(p.price)}</strong>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </aside>
        </div>
      )}
      {toast && (
        <div className="toast" role="status">
          {toast}
        </div>
      )}
    </div>
  );
}

const TRANSITIONS: Record<ListingStatus, { action: string; path: string; danger?: boolean; permission?: string }[]> = {
  DRAFT: [{ action: "activate", path: "activate" }],
  ACTIVE: [
    { action: "withdraw", path: "withdraw", danger: true },
    { action: "sold", path: "sold" },
  ],
  UNDER_OFFER: [{ action: "sold", path: "sold" }],
  WITHDRAWN: [{ action: "relist", path: "activate" }],
  EXPIRED: [{ action: "relist", path: "activate" }],
  SOLD: [],
};

function StatusCard({ token, propertyId, sale, onChanged, notify }: { token: string; propertyId: string; sale: SaleView; onChanged: (s: SaleView) => void; notify: (m: string) => void }) {
  const { t, i18n } = useTranslation();
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const date = (iso: string | null) => (iso ? new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium" }).format(new Date(iso)) : null);

  async function run(path: string, action: string) {
    let body: unknown = {};
    if (action === "withdraw") {
      const reason = window.prompt(t("sale.withdrawPrompt")) ?? "";
      body = { reason };
    }
    if (action === "sold") {
      const raw = window.prompt(t("sale.soldPrompt"), sale.price != null ? String(sale.price) : "");
      if (raw === null) return;
      const value = Number(raw.replace(/\s/g, "").replace(",", "."));
      body = { finalPrice: Number.isFinite(value) && value > 0 ? value : null };
    }
    setBusy(true);
    setErrors([]);
    try {
      onChanged(await api.post<SaleView>(`/api/properties/${propertyId}/sale/${path}`, body, token));
      notify(t(`sale.done.${action}`));
    } catch (err) {
      setErrors(err instanceof ApiError && err.errors.length ? err.errors.map((e) => e.code === "required" || e.code === "invalid" ? e.field : e.code) : ["generic"]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel status-card">
      <div className="status-card-head">
        <h2>{t("sale.statusTitle")}</h2>
        <StatusPill status={sale.status} />
      </div>
      <p className="hint">{t(`sale.statusHint.${sale.status}`)}</p>
      {sale.publishedAt && <p className="hint">{t("sale.listedSince", { date: date(sale.publishedAt) })}</p>}
      {sale.withdrawnReason && <p className="hint">{t("sale.withdrawnReason", { reason: sale.withdrawnReason })}</p>}
      {sale.closedAt && <p className="hint">{t("sale.closedAt", { date: date(sale.closedAt) })}</p>}
      {sale.status === "DRAFT" && sale.blockers.length > 0 && (
        <ul className="blockers">
          {sale.blockers.map((b) => (
            <li key={b}>
              {b === "propertyDraft" ? <Link to={`/properties/${propertyId}`}>{t(`sale.blockers.${b}`)}</Link> : t(`sale.blockers.${b}`)}
            </li>
          ))}
        </ul>
      )}
      {errors.map((e) => (
        <p key={e} className="error">
          {t(`sale.blockers.${e}`, { defaultValue: t("sale.actionFailed") })}
        </p>
      ))}
      <div className="status-actions">
        {TRANSITIONS[sale.status].map((tr) => (
          <button key={tr.action} type="button" className={tr.danger ? "button-secondary" : ""} disabled={busy || (tr.action === "activate" && sale.blockers.length > 0)} onClick={() => run(tr.path, tr.action)}>
            {t(`sale.actions.${tr.action}`)}
          </button>
        ))}
      </div>
    </div>
  );
}

function ShareCard({ sale, notify }: { sale: SaleView; notify: (m: string) => void }) {
  const { t, i18n } = useTranslation();
  const price = sale.price != null ? new Intl.NumberFormat(i18n.language, { style: "currency", currency: sale.currency, maximumFractionDigits: 0 }).format(sale.price) : "";
  const message = t("sale.shareMessage", { price, url: sale.publicUrl });

  async function copy(text: string, done: string) {
    try {
      await navigator.clipboard.writeText(text);
      notify(done);
    } catch {
      window.prompt(t("sale.copyManually"), text);
    }
  }

  return (
    <div className="panel">
      <h2>{t("sale.shareTitle")}</h2>
      <p className="hint">{t("sale.shareHint")}</p>
      <a className="share-link" href={sale.publicUrl!} target="_blank" rel="noreferrer">
        {sale.publicUrl}
      </a>
      <div className="form-actions">
        <button type="button" onClick={() => copy(sale.publicUrl!, t("sale.linkCopied"))}>
          {t("sale.copyLink")}
        </button>
        <button type="button" className="button-secondary" onClick={() => copy(message, t("sale.messageCopied"))}>
          {t("sale.copyMessage")}
        </button>
      </div>
    </div>
  );
}

// eslint-disable-next-line @typescript-eslint/naming-convention
function SaleForm_({ token, propertyId, sale, onSaved, notify }: { token: string; propertyId: string; sale: SaleView; onSaved: (s: SaleView) => void; notify: (m: string) => void }) {
  const { t } = useTranslation();
  const [price, setPrice] = useState(sale.price != null ? String(sale.price) : "");
  const [currency, setCurrency] = useState(sale.currency || "USD");
  const [seller, setSeller] = useState(sale.seller);
  const [mandateType, setMandateType] = useState<string>(sale.mandateType ?? "");
  const [mandateValidUntil, setMandateValidUntil] = useState(sale.mandateValidUntil ?? "");
  const [commissionPercent, setCommissionPercent] = useState(sale.commissionPercent != null ? String(sale.commissionPercent) : "");
  const [commissionFixed, setCommissionFixed] = useState(sale.commissionFixed != null ? String(sale.commissionFixed) : "");
  const [accessNotes, setAccessNotes] = useState(sale.accessNotes ?? "");
  const [hideExactAddress, setHideExactAddress] = useState(sale.hideExactAddress);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [ownerLink, setOwnerLink] = useState<OwnerLink | null>(null);
  const readOnly = sale.status === "SOLD";

  const parse = (raw: string) => {
    const n = Number(raw.replace(/\s/g, "").replace(",", "."));
    return raw.trim() === "" ? null : Number.isFinite(n) ? n : NaN;
  };
  const errorFor = (f: string) => (errors[f] ? t(`propertyForm.errors.${errors[f]}`, { defaultValue: t(`sale.errors.${errors[f]}`, { defaultValue: t("propertyForm.errors.invalid") }) }) : null);

  async function save() {
    setSaving(true);
    setErrors({});
    const form: SaleForm = {
      price: parse(price),
      currency,
      sellerClientId: seller?.id ?? null,
      mandateType: (mandateType || null) as MandateType | null,
      mandateValidUntil: mandateValidUntil || null,
      commissionPercent: parse(commissionPercent),
      commissionFixed: parse(commissionFixed),
      accessNotes: accessNotes.trim() || null,
      hideExactAddress,
    };
    try {
      onSaved(await api.put<SaleView>(`/api/properties/${propertyId}/sale`, form, token));
      notify(t("sale.saved"));
    } catch (err) {
      if (err instanceof ApiError && err.errors.length) setErrors(Object.fromEntries(err.errors.map((e) => [e.field, e.code])));
      else notify(t("sale.saveError"));
    } finally {
      setSaving(false);
    }
  }

  async function issueOwnerLink() {
    if (!seller) return;
    try {
      const link = await api.post<OwnerLink>(`/api/clients/${seller.id}/owner-link`, {}, token);
      setOwnerLink(link);
      await navigator.clipboard.writeText(link.url).catch(() => undefined);
      notify(t("sale.ownerLinkCopied"));
    } catch {
      notify(t("sale.actionFailed"));
    }
  }

  return (
    <form
      className="panel editor"
      onSubmit={(e) => {
        e.preventDefault();
        void save();
      }}
    >
      <h2>{t("sale.formTitle")}</h2>
      <div className="price-row">
        <Field label={t("propertyForm.fields.price")} required error={errorFor("price")} fieldKey="price">
          {(id, describedBy) => <NumberInput id={id} value={price} onChange={setPrice} unit={currency} decimal describedBy={describedBy} invalid={!!errorFor("price")} />}
        </Field>
        <Field label={t("propertyForm.fields.currency")} error={errorFor("currency")}>
          {() => <ChipGroup label={t("propertyForm.fields.currency")} allowClear={false} value={currency} options={CURRENCIES.map((c) => ({ value: c, label: c }))} onChange={setCurrency} />}
        </Field>
      </div>

      <h3 className="form-section">{t("sale.sellerTitle")}</h3>
      <SellerPicker token={token} value={seller} onChange={setSeller} error={errorFor("sellerClientId")} />
      {seller && (
        <div className="owner-link-row">
          <button type="button" className="button-secondary" onClick={issueOwnerLink}>
            {t("sale.issueOwnerLink")}
          </button>
          <span className="hint">{ownerLink ? t("sale.ownerLinkIssued", { date: new Date(ownerLink.expiresAt).toLocaleDateString() }) : t("sale.ownerLinkHint")}</span>
        </div>
      )}

      <h3 className="form-section">{t("sale.mandateTitle")}</h3>
      <div className="form-grid">
        <Field label={t("sale.fields.mandateType")} error={errorFor("mandateType")} wide>
          {() => (
            <ChipGroup
              label={t("sale.fields.mandateType")}
              value={mandateType}
              options={[
                { value: "EXCLUSIVE", label: t("sale.mandate.EXCLUSIVE") },
                { value: "NON_EXCLUSIVE", label: t("sale.mandate.NON_EXCLUSIVE") },
              ]}
              onChange={setMandateType}
            />
          )}
        </Field>
        <Field label={t("sale.fields.mandateValidUntil")} error={errorFor("mandateValidUntil")}>
          {(id) => <input id={id} type="date" value={mandateValidUntil} onChange={(e) => setMandateValidUntil(e.target.value)} />}
        </Field>
        <Field label={t("sale.fields.commissionPercent")} error={errorFor("commissionPercent")}>
          {(id) => <NumberInput id={id} value={commissionPercent} onChange={setCommissionPercent} unit="%" decimal />}
        </Field>
        <Field label={t("sale.fields.commissionFixed")} error={errorFor("commissionFixed")}>
          {(id) => <NumberInput id={id} value={commissionFixed} onChange={setCommissionFixed} unit={currency} decimal />}
        </Field>
      </div>

      <h3 className="form-section">{t("sale.accessTitle")}</h3>
      <Field label={t("sale.fields.accessNotes")} hint={t("sale.fields.accessHint")} error={errorFor("accessNotes")} wide>
        {(id) => <textarea id={id} rows={2} maxLength={500} value={accessNotes} onChange={(e) => setAccessNotes(e.target.value)} />}
      </Field>
      <label className="checkbox-row">
        <input type="checkbox" checked={hideExactAddress} onChange={(e) => setHideExactAddress(e.target.checked)} />
        <span>
          {t("sale.fields.hideExactAddress")}
          <span className="field-hint"> — {t("sale.fields.hideExactAddressHint")}</span>
        </span>
      </label>

      <div className="form-actions">
        <button type="submit" disabled={saving || readOnly}>
          {saving ? t("sale.saving") : t("sale.save")}
        </button>
        {readOnly && <span className="hint">{t("sale.readOnlySold")}</span>}
      </div>
    </form>
  );
}

function SellerPicker({ token, value, onChange, error }: { token: string; value: SaleView["seller"]; onChange: (v: SaleView["seller"]) => void; error: string | null }) {
  const { t } = useTranslation();
  const [clients, setClients] = useState<Client[]>([]);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);

  useEffect(() => {
    api.get<Client[]>("/api/clients", token).then(setClients).catch(() => undefined);
  }, [token]);

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    return clients.filter((c) => !q || `${c.firstName} ${c.lastName} ${c.phone ?? ""} ${c.email ?? ""}`.toLowerCase().includes(q)).slice(0, 8);
  }, [clients, query]);

  if (value) {
    return (
      <div className="seller-chip">
        <span>
          <strong>
            {value.firstName} {value.lastName}
          </strong>
          {value.phone && <span className="hint"> · {value.phone}</span>}
          {value.email && <span className="hint"> · {value.email}</span>}
        </span>
        <button type="button" className="link-button" onClick={() => onChange(null)}>
          {t("sale.changeSeller")}
        </button>
      </div>
    );
  }

  return (
    <div className="address-search">
      <div className="combobox">
        <span className="combobox-icon" aria-hidden="true">
          ⌕
        </span>
        <input
          placeholder={t("sale.sellerSearch")}
          value={query}
          aria-invalid={!!error || undefined}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 150)}
        />
      </div>
      {error && <span className="field-error">{error}</span>}
      {open && (
        <ul className="combobox-list" role="listbox">
          {matches.map((c) => (
            <li
              key={c.id}
              role="option"
              aria-selected={false}
              onMouseDown={(e) => {
                e.preventDefault();
                onChange({ id: c.id, firstName: c.firstName, lastName: c.lastName, phone: c.phone, email: c.email });
              }}
            >
              <span className="option-main">
                {c.firstName} {c.lastName}
              </span>
              <span className="option-sub">{[c.phone, c.email].filter(Boolean).join(" · ")}</span>
            </li>
          ))}
          <li className="combobox-empty">
            <Link to="/clients" onMouseDown={(e) => e.stopPropagation()}>
              {t("sale.createClient")}
            </Link>
          </li>
        </ul>
      )}
    </div>
  );
}

function AgentsCard({ token, propertyId, agents, onSaved, notify }: { token: string; propertyId: string; agents: AgentView[]; onSaved: (a: AgentView[]) => void; notify: (m: string) => void }) {
  const { t } = useTranslation();
  const [all, setAll] = useState<Agent[]>([]);
  const [editing, setEditing] = useState(false);
  const [lead, setLead] = useState(agents.find((a) => a.role === "LEAD")?.id ?? "");
  const [co, setCo] = useState<string[]>(agents.filter((a) => a.role !== "LEAD").map((a) => a.id));

  useEffect(() => {
    api.get<Agent[]>("/api/agents", token).then((list) => setAll(list.filter((a) => a.status === "ACTIVE"))).catch(() => undefined);
  }, [token]);

  async function save() {
    try {
      onSaved(await api.put<AgentView[]>(`/api/properties/${propertyId}/agents`, { leadAgentId: lead, coAgentIds: co.filter((id) => id !== lead) }, token));
      setEditing(false);
      notify(t("sale.agentsSaved"));
    } catch {
      notify(t("sale.actionFailed"));
    }
  }

  return (
    <div className="panel">
      <div className="status-card-head">
        <h2>{t("sale.agentsTitle")}</h2>
        {!editing && (
          <button type="button" className="link-button" onClick={() => setEditing(true)}>
            {t("sale.editAgents")}
          </button>
        )}
      </div>
      {!editing ? (
        <ul className="agent-list">
          {agents.map((a) => (
            <li key={a.id}>
              <strong>
                {a.firstName} {a.lastName}
              </strong>{" "}
              <span className="hint">{t(`sale.role.${a.role ?? "CO_AGENT"}`)}</span>
              {a.phone && <span className="hint"> · {a.phone}</span>}
            </li>
          ))}
        </ul>
      ) : (
        <div className="form">
          <Field label={t("sale.leadAgent")} required>
            {(id) => (
              <select id={id} value={lead} onChange={(e) => setLead(e.target.value)}>
                <option value="">—</option>
                {all.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.firstName} {a.lastName}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label={t("sale.coAgents")}>
            {() => (
              <div className="chip-group chip-group-wrap">
                {all
                  .filter((a) => a.id !== lead)
                  .map((a) => {
                    const on = co.includes(a.id);
                    return (
                      <button key={a.id} type="button" aria-pressed={on} className={`chip chip-toggle${on ? " chip-selected" : ""}`} onClick={() => setCo(on ? co.filter((x) => x !== a.id) : [...co, a.id])}>
                        {on && "✓ "}
                        {a.firstName} {a.lastName}
                      </button>
                    );
                  })}
              </div>
            )}
          </Field>
          <div className="form-actions">
            <button type="button" disabled={!lead} onClick={save}>
              {t("sale.save")}
            </button>
            <button type="button" className="button-secondary" onClick={() => setEditing(false)}>
              {t("clients.cancel")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
