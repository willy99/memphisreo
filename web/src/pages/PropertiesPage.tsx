import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { CreatePropertyRequest, Property, PropertyType } from "../api/types";

const PROPERTY_TYPES: PropertyType[] = ["APARTMENT", "HOUSE", "LAND", "COMMERCIAL", "OTHER"];

export function PropertiesPage() {
  const { token } = useAuth();
  const [properties, setProperties] = useState<Property[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  async function loadProperties() {
    if (!token) return;
    try {
      setProperties(await api.get<Property[]>("/api/properties", token));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Не вдалося завантажити об'єкти");
    }
  }

  useEffect(() => {
    loadProperties();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <div className="page">
      <div className="page-header">
        <h1>Об'єкти нерухомості</h1>
        <button onClick={() => setShowForm((v) => !v)}>{showForm ? "Скасувати" : "+ Новий об'єкт"}</button>
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
            <th>Тип</th>
            <th>Площа</th>
            <th>Кімнати</th>
            <th>Статус</th>
            <th>ID</th>
          </tr>
        </thead>
        <tbody>
          {properties.map((p) => (
            <tr key={p.id}>
              <td>{p.type}</td>
              <td>{p.areaSqm} м²</td>
              <td>{p.rooms ?? "—"}</td>
              <td>{p.status}</td>
              <td className="mono">{p.id}</td>
            </tr>
          ))}
          {properties.length === 0 && (
            <tr>
              <td colSpan={5}>Поки що немає жодного об'єкта</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function CreatePropertyForm({ token, onCreated }: { token: string; onCreated: () => void }) {
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
      setError(err instanceof ApiError ? err.message : "Не вдалося створити об'єкт");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="form form-inline">
      <label>
        Тип
        <select name="type" defaultValue="APARTMENT">
          {PROPERTY_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </label>
      <label>
        Площа, м²
        <input name="areaSqm" type="number" step="0.1" required />
      </label>
      <label>
        Кімнати
        <input name="rooms" type="number" />
      </label>
      <label>
        Країна
        <input name="countryCode" defaultValue="UA" maxLength={2} required />
      </label>
      <label>
        Місто
        <input name="city" required />
      </label>
      <label>
        Вулиця
        <input name="street" required />
      </label>
      <label>
        Будинок
        <input name="houseNumber" required />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit" disabled={submitting}>
        {submitting ? "Створюємо…" : "Створити"}
      </button>
    </form>
  );
}
