import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../../api/client";
import type { PublicCard, SearchResult } from "../../api/types";
import { PROPERTY_TYPES } from "../properties/propertyFields";
import { useAgency } from "./PublicLayout";
import { addressLine, facts, isNew, money, pricePerUnit } from "./publicFormat";

const PublicMap = lazy(() => import("./PublicMap"));

const SORTS = ["newest", "priceAsc", "priceDesc", "areaDesc"] as const;
const QUICK_FEATURES = ["SHELTER", "BACKUP_POWER", "UNDERGROUND_PARKING", "BALCONY"] as const;

/** Публічний список об'єктів агенції: фільтри в URL, список + мапа. */
export function PublicAgencyPage() {
  const { t, i18n } = useTranslation();
  const { slug } = useParams();
  const agency = useAgency();
  const [params, setParams] = useSearchParams();
  const [result, setResult] = useState<SearchResult | null>(null);
  const [view, setView] = useState<"list" | "map">("list");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [moreFilters, setMoreFilters] = useState(false);

  const query = params.toString();
  useEffect(() => {
    if (!slug) return;
    api.get<SearchResult>(`/api/public/agencies/${slug}/properties?${query}`, "").then(setResult).catch(() => setResult({ items: [], total: 0, page: 0, size: 24, districts: [], minPrice: null, maxPrice: null }));
  }, [slug, query]);

  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    next.delete("page");
    setParams(next, { replace: true });
  };
  const get = (key: string) => params.get(key) ?? "";
  const activeFilters = ["type", "roomsMin", "priceMin", "priceMax", "district", "market", "features", "areaMin", "areaMax"].filter((k) => get(k)).length;
  const formatPin = useCallback((c: PublicCard) => money(c.price, c.currency, i18n.language, true), [i18n.language]);
  const sotka = t("propertyForm.units.sotka");
  const pages = result ? Math.ceil(result.total / result.size) : 0;
  const features = useMemo(() => get("features").split(",").filter(Boolean), [params]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="public-search">
      {agency?.about && params.size === 0 && <p className="public-about">{agency.about}</p>}

      <div className="filter-bar">
        <select aria-label={t("public.filters.type")} value={get("type")} onChange={(e) => set("type", e.target.value)}>
          <option value="">{t("public.filters.anyType")}</option>
          {PROPERTY_TYPES.map((p) => (
            <option key={p} value={p}>
              {t(`propertyTypes.${p}`)}
            </option>
          ))}
        </select>
        <div className="filter-rooms" role="group" aria-label={t("public.filters.rooms")}>
          {["", "1", "2", "3", "4"].map((r) => (
            <button key={r} type="button" className={`chip${get("roomsMin") === r ? " chip-selected" : ""}`} onClick={() => set("roomsMin", r)}>
              {r === "" ? t("public.filters.anyRooms") : `${r}+`}
            </button>
          ))}
        </div>
        <input className="filter-price" inputMode="numeric" placeholder={t("public.filters.priceFrom")} value={get("priceMin")} onChange={(e) => set("priceMin", e.target.value.replace(/\D/g, ""))} />
        <input className="filter-price" inputMode="numeric" placeholder={t("public.filters.priceTo")} value={get("priceMax")} onChange={(e) => set("priceMax", e.target.value.replace(/\D/g, ""))} />
        {result && result.districts.length > 1 && (
          <select aria-label={t("public.filters.district")} value={get("district")} onChange={(e) => set("district", e.target.value)}>
            <option value="">{t("public.filters.anyDistrict")}</option>
            {result.districts.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        )}
        <button type="button" className="button-secondary" onClick={() => setMoreFilters((v) => !v)}>
          {t("public.filters.more")}
          {activeFilters > 0 && <span className="media-tab-count">{activeFilters}</span>}
        </button>
        <div className="filter-spacer" />
        <select aria-label={t("public.sort.label")} value={get("sort") || "newest"} onChange={(e) => set("sort", e.target.value)}>
          {SORTS.map((s) => (
            <option key={s} value={s}>
              {t(`public.sort.${s}`)}
            </option>
          ))}
        </select>
        <div className="segmented view-toggle" role="tablist">
          <button type="button" role="tab" aria-selected={view === "list"} className={view === "list" ? "active" : ""} onClick={() => setView("list")}>
            {t("public.viewList")}
          </button>
          <button type="button" role="tab" aria-selected={view === "map"} className={view === "map" ? "active" : ""} onClick={() => setView("map")}>
            {t("public.viewMap")}
          </button>
        </div>
      </div>

      {moreFilters && (
        <div className="filter-more">
          <div className="filter-group">
            <span className="field-label">{t("public.filters.market")}</span>
            <div className="chip-group">
              {["", "SECONDARY", "NEW_BUILD"].map((m) => (
                <button key={m} type="button" className={`chip${get("market") === m ? " chip-selected" : ""}`} onClick={() => set("market", m)}>
                  {m ? t(`propertyForm.options.market.${m}`) : t("public.filters.any")}
                </button>
              ))}
            </div>
          </div>
          <div className="filter-group">
            <span className="field-label">{t("public.filters.area")}</span>
            <div className="filter-inline">
              <input inputMode="numeric" placeholder={t("public.filters.from")} value={get("areaMin")} onChange={(e) => set("areaMin", e.target.value.replace(/\D/g, ""))} />
              <input inputMode="numeric" placeholder={t("public.filters.to")} value={get("areaMax")} onChange={(e) => set("areaMax", e.target.value.replace(/\D/g, ""))} />
              <span className="hint">м²</span>
            </div>
          </div>
          <div className="filter-group">
            <span className="field-label">{t("public.filters.features")}</span>
            <div className="chip-group chip-group-wrap">
              {QUICK_FEATURES.map((f) => {
                const on = features.includes(f);
                return (
                  <button key={f} type="button" aria-pressed={on} className={`chip chip-toggle${on ? " chip-selected" : ""}`} onClick={() => set("features", (on ? features.filter((x) => x !== f) : [...features, f]).join(","))}>
                    {on && "✓ "}
                    {t(`propertyForm.features.${f}`)}
                  </button>
                );
              })}
            </div>
          </div>
          {activeFilters > 0 && (
            <button type="button" className="link-button" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
              {t("public.filters.reset")}
            </button>
          )}
        </div>
      )}

      {result && (
        <p className="page-subtitle public-count">{t("public.found", { count: result.total })}</p>
      )}

      <div className={`public-results${view === "map" ? " map-first" : ""}`}>
        <ul className="property-grid public-grid">
          {result?.items.map((c) => (
            <li key={c.id} onMouseEnter={() => setActiveId(c.id)} onMouseLeave={() => setActiveId(null)}>
              <Link to={`/p/${slug}/${c.id}`} className={`property-card${activeId === c.id ? " highlighted" : ""}`}>
                <span className="property-cover">
                  {c.coverUrl ? <img src={c.coverUrl} alt="" loading="lazy" /> : <span className="property-cover-empty">{t("properties.noPhoto")}</span>}
                  <span className="card-badges">
                    {isNew(c.publishedAt) && <span className="badge">{t("public.badges.new")}</span>}
                    {c.priceReduced && <span className="badge badge-accent">{t("public.badges.reduced")}</span>}
                  </span>
                  {c.photoCount > 0 && <span className="photo-count">📷 {c.photoCount}</span>}
                </span>
                <span className="property-body">
                  <span className="property-price">
                    {money(c.price, c.currency, i18n.language)}
                    {pricePerUnit(c, i18n.language, sotka) && <span className="price-per"> · {pricePerUnit(c, i18n.language, sotka)}</span>}
                  </span>
                  <span className="property-title">{c.title || t(`propertyTypes.${c.type}`)}</span>
                  <span className="property-address">{addressLine(c) || c.city}</span>
                  <span className="property-facts">{[t(`propertyTypes.${c.type}`), ...facts(c, t)].join(" · ")}</span>
                </span>
              </Link>
            </li>
          ))}
          {result && result.items.length === 0 && (
            <li className="empty-state">
              <h2>{t("public.nothingFound")}</h2>
            </li>
          )}
        </ul>
        <div className="public-map-column">
          <Suspense fallback={<div className="public-map map-loading">{t("propertyForm.map.loading")}</div>}>
            <PublicMap items={result?.items ?? []} activeId={activeId} onSelect={setActiveId} formatPrice={formatPin} />
          </Suspense>
        </div>
      </div>

      {pages > 1 && result && (
        <nav className="pagination" aria-label={t("public.pagination")}>
          {Array.from({ length: pages }, (_, i) => (
            <button key={i} type="button" className={`chip${result.page === i ? " chip-selected" : ""}`} onClick={() => set("page", String(i))}>
              {i + 1}
            </button>
          ))}
        </nav>
      )}
    </div>
  );
}
