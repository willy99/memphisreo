import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { PropertyCard } from "../api/types";
import { StatusPill } from "../components/StatusPill";
import { SQM_PER_SOTKA } from "./properties/propertyFields";

type Filter = "all" | "DRAFT" | "ACTIVE";

export function PropertiesPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [cards, setCards] = useState<PropertyCard[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");

  useEffect(() => {
    if (!token) return;
    api
      .get<PropertyCard[]>("/api/properties", token)
      .then(setCards)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("properties.loadError")));
  }, [token, t]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (cards ?? []).filter((c) => {
      if (filter !== "all" && c.status !== filter) return false;
      if (!q) return true;
      return [c.title, c.city, c.district, c.street, c.houseNumber, c.complexName].some((x) => x?.toLowerCase().includes(q));
    });
  }, [cards, filter, query]);

  const counts = useMemo(
    () => ({
      all: cards?.length ?? 0,
      DRAFT: cards?.filter((c) => c.status === "DRAFT").length ?? 0,
      ACTIVE: cards?.filter((c) => c.status === "ACTIVE").length ?? 0,
    }),
    [cards],
  );

  const money = (amount: number, currency: string) =>
    new Intl.NumberFormat(i18n.language, { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>{t("properties.title")}</h1>
          {cards && <p className="page-subtitle">{t("properties.total", { count: cards.length })}</p>}
        </div>
        <Link to="/properties/new" className="button-link">
          + {t("properties.newButton")}
        </Link>
      </div>

      {error && <p className="error">{error}</p>}

      {cards && cards.length > 0 && (
        <div className="list-toolbar">
          <div className="segmented" role="tablist">
            {(["all", "DRAFT", "ACTIVE"] as Filter[]).map((f) => (
              <button key={f} type="button" role="tab" aria-selected={filter === f} className={filter === f ? "active" : ""} onClick={() => setFilter(f)}>
                {t(`properties.filters.${f}`)} <span className="media-tab-count">{counts[f]}</span>
              </button>
            ))}
          </div>
          <input type="search" className="list-search" placeholder={t("properties.searchPlaceholder")} value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      )}

      {cards && cards.length === 0 && (
        <div className="empty-state">
          <span className="empty-icon" aria-hidden="true">
            🏡
          </span>
          <h2>{t("properties.emptyTitle")}</h2>
          <p className="hint">{t("properties.emptyHint")}</p>
          <Link to="/properties/new" className="button-link">
            + {t("properties.newButton")}
          </Link>
        </div>
      )}

      <ul className="property-grid">
        {visible.map((c) => (
          <li key={c.id}>
            <button type="button" className="property-card" onClick={() => navigate(`/properties/${c.id}`)}>
              <span className="property-cover">
                {c.coverThumbUrl ? <img src={c.coverThumbUrl} alt="" loading="lazy" /> : <span className="property-cover-empty">{t("properties.noPhoto")}</span>}
                <span className="property-status">
                  <StatusPill status={c.status} />
                </span>
                {c.photoCount > 0 && <span className="photo-count">📷 {c.photoCount}</span>}
              </span>
              <span className="property-body">
                <span className="property-price">{c.price != null && c.currency ? money(c.price, c.currency) : t("properties.noPrice")}</span>
                <span className="property-title">{c.title || summary(c, t)}</span>
                <span className="property-address">{address(c) || t("properties.noAddress")}</span>
                <span className="property-facts">
                  {[
                    t(`propertyTypes.${c.type}`),
                    c.rooms ? t("properties.roomsShort", { count: c.rooms }) : null,
                    c.areaSqm ? `${c.areaSqm} м²` : null,
                    c.landAreaSqm ? `${+(c.landAreaSqm / SQM_PER_SOTKA).toFixed(2)} ${t("propertyForm.units.sotka")}` : null,
                    c.floor != null && c.totalFloors ? t("properties.floorOf", { floor: c.floor, total: c.totalFloors }) : null,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
      {cards && cards.length > 0 && visible.length === 0 && <p className="hint">{t("properties.nothingFound")}</p>}
    </div>
  );
}

function address(c: PropertyCard): string {
  return [c.city, c.district, [c.street, c.houseNumber].filter(Boolean).join(", "), c.complexName ? `ЖК ${c.complexName}` : null]
    .filter(Boolean)
    .join(", ");
}

function summary(c: PropertyCard, t: (key: string, opts?: Record<string, unknown>) => string): string {
  return c.rooms && c.type === "APARTMENT" ? t("properties.apartmentSummary", { count: c.rooms }) : t(`propertyTypes.${c.type}`);
}
