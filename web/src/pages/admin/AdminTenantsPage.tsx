import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { usePlatformAuth } from "../../auth/PlatformAuthContext";
import { api, ApiError } from "../../api/client";
import type { RegisterTenantRequest, RegisterTenantResponse, TenantPage } from "../../api/types";
import { StatusPill } from "../../components/StatusPill";

// Країни, засіяні в control_plane.country (V2__seed_countries.sql).
const COUNTRIES = ["UA", "DE"] as const;

export function AdminTenantsPage() {
  const { t, i18n } = useTranslation();
  const { token } = usePlatformAuth();
  const navigate = useNavigate();
  const [page, setPage] = useState<TenantPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [created, setCreated] = useState<{ name: string; email: string; password: string } | null>(null);

  async function load() {
    if (!token) return;
    try {
      setPage(await api.get<TenantPage>("/platform-admin/tenants?size=100", token));
    } catch {
      setError(t("admin.loadError"));
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  const dateFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium" });

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{t("admin.tenantsTitle")}</h1>
          {page && <p className="page-subtitle">{t("admin.total", { count: page.total })}</p>}
        </div>
        <button
          onClick={() => {
            setShowForm((v) => !v);
            setCreated(null);
          }}
        >
          {showForm ? t("admin.cancel") : t("admin.newTenant")}
        </button>
      </div>

      {created && (
        <div className="invite-banner">
          <strong>{t("admin.createdTitle", { name: created.name })}</strong>
          <p className="hint">{t("admin.createdHint")}</p>
          <code>
            {t("login.email")}: {created.email}
            <br />
            {t("login.password")}: {created.password}
          </code>
        </div>
      )}

      {showForm && token && (
        <CreateTenantForm
          token={token}
          onCreated={(request) => {
            setShowForm(false);
            setCreated({ name: request.agencyName, email: request.adminEmail, password: request.adminPassword });
            load();
          }}
        />
      )}

      {error && <p className="error">{error}</p>}

      <table className="table table-clickable">
        <thead>
          <tr>
            <th>{t("admin.columns.name")}</th>
            <th>{t("admin.columns.slug")}</th>
            <th>{t("admin.columns.country")}</th>
            <th>{t("admin.columns.status")}</th>
            <th className="num">{t("admin.columns.agents")}</th>
            <th className="num">{t("admin.columns.properties")}</th>
            <th>{t("admin.columns.created")}</th>
          </tr>
        </thead>
        <tbody>
          {page?.items.map((tenant) => (
            <tr key={tenant.id} onClick={() => navigate(`/admin/tenants/${tenant.id}`)}>
              <td className="strong">{tenant.name}</td>
              <td className="mono">{tenant.slug}</td>
              <td>{t(`countries.${tenant.countryCode}`, { defaultValue: tenant.countryCode })}</td>
              <td>
                <StatusPill status={tenant.status} />
              </td>
              <td className="num">{tenant.agents}</td>
              <td className="num">{tenant.properties}</td>
              <td>{dateFormat.format(new Date(tenant.createdAt))}</td>
            </tr>
          ))}
          {page && page.items.length === 0 && (
            <tr>
              <td colSpan={7}>{t("admin.empty")}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function CreateTenantForm({
  token,
  onCreated,
}: {
  token: string;
  onCreated: (request: RegisterTenantRequest) => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<RegisterTenantRequest>({
    agencyName: "",
    slug: "",
    countryCode: "UA",
    adminEmail: "",
    adminPassword: generatePassword(),
    adminFirstName: "",
    adminLastName: "",
  });
  const [slugTouched, setSlugTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update<K extends keyof RegisterTenantRequest>(key: K, value: RegisterTenantRequest[K]) {
    setForm((f) => {
      const next = { ...f, [key]: value };
      if (key === "agencyName" && !slugTouched) next.slug = slugify(String(value));
      return next;
    });
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.post<RegisterTenantResponse>("/platform-admin/tenants", form, token);
      onCreated(form);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("admin.createError"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="form panel">
      <h2>{t("admin.formTitle")}</h2>
      <div className="form-grid">
        <label>
          {t("admin.fields.agencyName")}
          <input value={form.agencyName} onChange={(e) => update("agencyName", e.target.value)} required autoFocus />
        </label>
        <label>
          {t("admin.fields.slug")}
          <input
            value={form.slug}
            onChange={(e) => {
              setSlugTouched(true);
              update("slug", e.target.value);
            }}
            pattern="[a-z0-9][a-z0-9\-]{1,48}[a-z0-9]"
            title={t("admin.fields.slugHint")}
            required
            className="mono-input"
          />
        </label>
        <label>
          {t("admin.fields.country")}
          <select value={form.countryCode} onChange={(e) => update("countryCode", e.target.value)}>
            {COUNTRIES.map((c) => (
              <option key={c} value={c}>
                {t(`countries.${c}`)}
              </option>
            ))}
          </select>
        </label>
      </div>

      <h3 className="form-section">{t("admin.adminSection")}</h3>
      <div className="form-grid">
        <label>
          {t("admin.fields.firstName")}
          <input value={form.adminFirstName} onChange={(e) => update("adminFirstName", e.target.value)} required />
        </label>
        <label>
          {t("admin.fields.lastName")}
          <input value={form.adminLastName} onChange={(e) => update("adminLastName", e.target.value)} required />
        </label>
        <label>
          {t("login.email")}
          <input type="email" value={form.adminEmail} onChange={(e) => update("adminEmail", e.target.value)} required />
        </label>
        <label>
          <span className="label-row">
            {t("admin.fields.password")}
            <button type="button" className="link-button" onClick={() => update("adminPassword", generatePassword())}>
              {t("admin.generate")}
            </button>
          </span>
          <input
            value={form.adminPassword}
            onChange={(e) => update("adminPassword", e.target.value)}
            minLength={8}
            required
            className="mono-input"
          />
        </label>
      </div>
      <p className="hint">{t("admin.rolesHint")}</p>

      {error && <p className="error">{error}</p>}
      <div>
        <button type="submit" disabled={submitting}>
          {submitting ? t("admin.creating") : t("admin.create")}
        </button>
      </div>
    </form>
  );
}

const TRANSLIT: Record<string, string> = {
  а: "a", б: "b", в: "v", г: "h", ґ: "g", д: "d", е: "e", є: "ie", ж: "zh", з: "z", и: "y", і: "i", ї: "i",
  й: "i", к: "k", л: "l", м: "m", н: "n", о: "o", п: "p", р: "r", с: "s", т: "t", у: "u", ф: "f", х: "kh",
  ц: "ts", ч: "ch", ш: "sh", щ: "shch", ь: "", ю: "iu", я: "ia", "'": "", "’": "",
};

/** Назва агенції → slug (українська транслітерація за КМУ-2010, спрощено). */
function slugify(name: string): string {
  return name
    .toLowerCase()
    .split("")
    .map((ch) => TRANSLIT[ch] ?? ch)
    .join("")
    .normalize("NFKD")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 50);
}

function generatePassword(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(14));
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}
