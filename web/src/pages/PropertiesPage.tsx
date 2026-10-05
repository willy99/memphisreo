import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { CreatePropertyRequest, Property, PropertyType } from "../api/types";
import { StatusPill } from "../components/StatusPill";

const PROPERTY_TYPES: PropertyType[] = ["APARTMENT", "HOUSE", "LAND", "COMMERCIAL", "OTHER"];

export function PropertiesPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [properties, setProperties] = useState<Property[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  async function loadProperties() {
    if (!token) return;
    try {
      setProperties(await api.get<Property[]>("/api/properties", token));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("properties.loadError"));
    }
  }

  useEffect(() => {
    loadProperties();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("properties.title")}</h1>
        <button onClick={() => setShowForm((v) => !v)}>
          {showForm ? t("properties.cancelButton") : t("properties.newButton")}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {showForm && token && (
        <CreatePropertyForm
          token={token}
          onCreated={() => {
            setShowForm(false);
            loadProperties();
          }}
        />
      )}

      <table className="table">
        <thead>
          <tr>
            <th>{t("properties.columns.type")}</th>
            <th>{t("properties.columns.area")}</th>
            <th>{t("properties.columns.rooms")}</th>
            <th>{t("properties.columns.status")}</th>
            <th>{t("properties.columns.id")}</th>
          </tr>
        </thead>
        <tbody>
          {properties.map((p) => (
            <tr key={p.id}>
              <td>{p.type}</td>
              <td className="num">{p.areaSqm} м²</td>
              <td className="num">{p.rooms ?? "—"}</td>
              <td>
                <StatusPill status={p.status} />
              </td>
              <td className="mono">{p.id}</td>
            </tr>
          ))}
          {properties.length === 0 && (
            <tr>
              <td colSpan={5}>{t("properties.empty")}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function CreatePropertyForm({ token, onCreated }: { token: string; onCreated: () => void }) {
  const { t } = useTranslation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    const request: CreatePropertyRequest = {
      type: form.get("type") as PropertyType,
      areaSqm: Number(form.get("areaSqm")),
      rooms: form.get("rooms") ? Number(form.get("rooms")) : undefined,
      countryCode: String(form.get("countryCode")),
      city: String(form.get("city")),
      street: String(form.get("street")),
      houseNumber: String(form.get("houseNumber")),
    };
    try {
      await api.post<void>("/api/properties", request, token);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("properties.createError"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="form form-inline">
      <label>
        {t("properties.form.type")}
        <select name="type" defaultValue="APARTMENT">
          {PROPERTY_TYPES.map((propertyType) => (
            <option key={propertyType} value={propertyType}>
              {propertyType}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t("properties.form.area")}
        <input name="areaSqm" type="number" step="0.1" required />
      </label>
      <label>
        {t("properties.form.rooms")}
        <input name="rooms" type="number" />
      </label>
      <label>
        {t("properties.form.country")}
        <input name="countryCode" defaultValue="UA" maxLength={2} required />
      </label>
      <label>
        {t("properties.form.city")}
        <input name="city" required />
      </label>
      <label>
        {t("properties.form.street")}
        <input name="street" required />
      </label>
      <label>
        {t("properties.form.houseNumber")}
        <input name="houseNumber" required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={submitting}>
        {submitting ? t("properties.form.submitting") : t("properties.form.submit")}
      </button>
    </form>
  );
}
