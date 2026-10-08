import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { CreateListingRequest, DealType, Listing, PropertyCard as Property } from "../api/types";
import { StatusPill } from "../components/StatusPill";

const DEAL_TYPES: DealType[] = ["SALE", "LONG_TERM_RENT", "SHORT_TERM_RENT"];

export function ListingsPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [listings, setListings] = useState<Listing[]>([]);
  const [properties, setProperties] = useState<Property[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  async function loadAll() {
    if (!token) return;
    try {
      const [listingsResult, propertiesResult] = await Promise.all([
        api.get<Listing[]>("/api/listings", token),
        api.get<Property[]>("/api/properties", token),
      ]);
      setListings(listingsResult);
      setProperties(propertiesResult);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("listings.loadError"));
    }
  }

  useEffect(() => {
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("listings.title")}</h1>
        <button onClick={() => setShowForm((v) => !v)} disabled={properties.length === 0}>
          {showForm ? t("listings.cancelButton") : t("listings.newButton")}
        </button>
      </div>

      {properties.length === 0 && <p className="hint">{t("listings.needProperty")}</p>}
      {error && <p className="error">{error}</p>}

      {showForm && token && (
        <CreateListingForm
          token={token}
          properties={properties}
          onCreated={() => {
            setShowForm(false);
            loadAll();
          }}
        />
      )}

      <table className="table">
        <thead>
          <tr>
            <th>{t("listings.columns.dealType")}</th>
            <th>{t("listings.columns.price")}</th>
            <th>{t("listings.columns.status")}</th>
            <th>{t("listings.columns.propertyId")}</th>
          </tr>
        </thead>
        <tbody>
          {listings.map((l) => (
            <tr key={l.id}>
              <td>{l.dealType}</td>
              <td className="num">
                {l.price.toLocaleString()} {l.currency}
              </td>
              <td>
                <StatusPill status={l.status} />
              </td>
              <td className="mono">{l.propertyId}</td>
            </tr>
          ))}
          {listings.length === 0 && (
            <tr>
              <td colSpan={4}>{t("listings.empty")}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function CreateListingForm({
  token,
  properties,
  onCreated,
}: {
  token: string;
  properties: Property[];
  onCreated: () => void;
}) {
  const { t } = useTranslation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    const request: CreateListingRequest = {
      propertyId: String(form.get("propertyId")),
      dealType: form.get("dealType") as DealType,
      price: Number(form.get("price")),
      currency: String(form.get("currency")),
    };
    try {
      await api.post<void>("/api/listings", request, token);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("listings.createError"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="form form-inline">
      <label>
        {t("listings.form.property")}
        <select name="propertyId" required>
          {properties.map((p) => (
            <option key={p.id} value={p.id}>
              {p.type} · {p.areaSqm} м² · {p.id.slice(0, 8)}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t("listings.form.dealType")}
        <select name="dealType" defaultValue="SALE">
          {DEAL_TYPES.map((dealType) => (
            <option key={dealType} value={dealType}>
              {dealType}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t("listings.form.price")}
        <input name="price" type="number" step="0.01" required />
      </label>
      <label>
        {t("listings.form.currency")}
        <input name="currency" defaultValue="USD" maxLength={3} required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={submitting}>
        {submitting ? t("listings.form.submitting") : t("listings.form.submit")}
      </button>
    </form>
  );
}
