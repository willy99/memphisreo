import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../../api/client";
import type { GeoPlace } from "../../api/types";
import { Field } from "../../components/form/Field";

const LocationMap = lazy(() => import("./LocationMap"));

export interface AddressValues {
  city: string;
  district: string;
  region: string;
  street: string;
  houseNumber: string;
  postalCode: string;
  complexName: string;
  latitude: number | null;
  longitude: number | null;
  geocodeSource: "AUTOCOMPLETE" | "PIN" | "MANUAL" | null;
}

interface AddressSectionProps {
  token: string;
  value: AddressValues;
  onChange: (patch: Partial<AddressValues>) => void;
  unitNumber: string;
  onUnitNumberChange: (value: string) => void;
  showUnit: boolean;
  showComplex: boolean;
  required: (field: string) => boolean;
  error: (field: string) => string | null;
}

/**
 * Адреса: пошук з автодоповненням → пін на мапі (можна перетягнути) →
 * структуровані поля, які завжди можна виправити вручну. Перетягування пін-а
 * пропонує оновити адресу, але не перезаписує введене мовчки.
 */
export function AddressSection(props: AddressSectionProps) {
  const { t } = useTranslation();
  const { token, value, onChange, required, error } = props;
  const [suggestion, setSuggestion] = useState<GeoPlace | null>(null);

  function applyPlace(place: GeoPlace, source: "AUTOCOMPLETE" | "PIN") {
    onChange({
      city: place.city ?? "",
      district: place.district ?? "",
      region: place.region ?? "",
      street: place.street ?? "",
      houseNumber: place.houseNumber ?? "",
      postalCode: place.postcode ?? "",
      latitude: place.latitude,
      longitude: place.longitude,
      geocodeSource: source,
    });
  }

  async function handlePick(latitude: number, longitude: number) {
    onChange({ latitude, longitude, geocodeSource: "PIN" });
    try {
      const place = await api.get<GeoPlace | undefined>(`/api/geo/reverse?lat=${latitude}&lon=${longitude}`, token);
      if (!place) return;
      const empty = !value.city && !value.street && !value.houseNumber;
      if (empty) {
        applyPlace({ ...place, latitude, longitude }, "PIN");
      } else if (place.street !== value.street || place.houseNumber !== value.houseNumber || place.city !== value.city) {
        setSuggestion({ ...place, latitude, longitude });
      }
    } catch {
      // геокодер недоступний — пін збережено, адресу агент вводить вручну
    }
  }

  const text = (field: keyof AddressValues, label: string, opts: { autoComplete?: string; wide?: boolean } = {}) => (
    <Field label={label} required={required(`address.${field}`)} error={error(`address.${field}`)} fieldKey={`address.${field}`} wide={opts.wide}>
      {(id, describedBy) => (
        <input
          id={id}
          value={(value[field] as string) ?? ""}
          autoComplete={opts.autoComplete ?? "off"}
          aria-describedby={describedBy}
          aria-invalid={!!error(`address.${field}`) || undefined}
          onChange={(e) => onChange({ [field]: e.target.value, geocodeSource: value.geocodeSource ?? "MANUAL" })}
        />
      )}
    </Field>
  );

  return (
    <div className="address-section">
      <AddressSearch token={token} bias={value} onSelect={(place) => applyPlace(place, "AUTOCOMPLETE")} />

      <div className="map-frame">
        <Suspense fallback={<div className="location-map map-loading">{t("propertyForm.map.loading")}</div>}>
          <LocationMap
            latitude={value.latitude}
            longitude={value.longitude}
            onPick={handlePick}
            label={t("propertyForm.map.label")}
            locale={{
              "CooperativeGesturesHandler.WindowsHelpText": t("propertyForm.map.gestureWindows"),
              "CooperativeGesturesHandler.MacHelpText": t("propertyForm.map.gestureMac"),
              "CooperativeGesturesHandler.MobileHelpText": t("propertyForm.map.gestureMobile"),
              "NavigationControl.ZoomIn": t("propertyForm.map.zoomIn"),
              "NavigationControl.ZoomOut": t("propertyForm.map.zoomOut"),
            }}
          />
        </Suspense>
        <p className="map-hint">
          {value.latitude !== null ? t("propertyForm.map.hintPlaced") : t("propertyForm.map.hintEmpty")}
          {value.latitude !== null && (
            <button type="button" className="link-button" onClick={() => onChange({ latitude: null, longitude: null })}>
              {t("propertyForm.map.clear")}
            </button>
          )}
        </p>
        {error("location") && <p className="field-error">{error("location")}</p>}
      </div>

      {suggestion && (
        <div className="inline-banner" role="status">
          <span>
            {t("propertyForm.map.suggest")} <strong>{suggestion.label}</strong>
          </span>
          <span className="inline-banner-actions">
            <button
              type="button"
              onClick={() => {
                applyPlace(suggestion, "PIN");
                setSuggestion(null);
              }}
            >
              {t("propertyForm.map.apply")}
            </button>
            <button type="button" className="button-secondary" onClick={() => setSuggestion(null)}>
              {t("propertyForm.map.keep")}
            </button>
          </span>
        </div>
      )}

      <div className="form-grid">
        {text("city", t("propertyForm.fields.city"), { autoComplete: "address-level2" })}
        {text("district", t("propertyForm.fields.district"))}
        {text("street", t("propertyForm.fields.street"), { autoComplete: "address-line1" })}
        {text("houseNumber", t("propertyForm.fields.houseNumber"))}
        {props.showUnit && (
          <Field label={t("propertyForm.fields.unitNumber")} error={error("unitNumber")} fieldKey="unitNumber">
            {(id) => <input id={id} value={props.unitNumber} onChange={(e) => props.onUnitNumberChange(e.target.value)} />}
          </Field>
        )}
        {props.showComplex && text("complexName", t("propertyForm.fields.complexName"))}
        {text("postalCode", t("propertyForm.fields.postalCode"), { autoComplete: "postal-code" })}
        {text("region", t("propertyForm.fields.region"), { autoComplete: "address-level1" })}
      </div>
    </div>
  );
}

/** Комбобокс пошуку адреси: 300 мс debounce, ≥3 символи, клавіатура ↑/↓/Enter/Esc. */
function AddressSearch({
  token,
  bias,
  onSelect,
}: {
  token: string;
  bias: AddressValues;
  onSelect: (place: GeoPlace) => void;
}) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GeoPlace[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [loading, setLoading] = useState(false);
  const requestId = useRef(0);

  useEffect(() => {
    const q = query.trim();
    if (q.length < 3) {
      setResults([]);
      return;
    }
    const id = ++requestId.current;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      const params = new URLSearchParams({ q });
      if (bias.latitude !== null && bias.longitude !== null) {
        params.set("lat", String(bias.latitude));
        params.set("lon", String(bias.longitude));
      }
      try {
        const places = await api.get<GeoPlace[]>(`/api/geo/search?${params}`, token);
        if (id === requestId.current) {
          setResults(places);
          setActive(places.length ? 0 : -1);
          setOpen(true);
        }
      } catch {
        if (id === requestId.current) setResults([]);
      } finally {
        if (id === requestId.current) setLoading(false);
      }
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query, token, bias.latitude, bias.longitude]);

  function choose(place: GeoPlace) {
    onSelect(place);
    setQuery("");
    setResults([]);
    setOpen(false);
  }

  return (
    <div className="address-search">
      <label htmlFor="address-search" className="field-label">
        {t("propertyForm.search.label")}
      </label>
      <div className="combobox">
        <span className="combobox-icon" aria-hidden="true">
          ⌕
        </span>
        <input
          id="address-search"
          role="combobox"
          aria-expanded={open && results.length > 0}
          aria-controls="address-search-list"
          aria-activedescendant={active >= 0 ? `address-option-${active}` : undefined}
          autoComplete="off"
          placeholder={t("propertyForm.search.placeholder")}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => results.length && setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 150)}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setActive((i) => Math.min(i + 1, results.length - 1));
            } else if (e.key === "ArrowUp") {
              e.preventDefault();
              setActive((i) => Math.max(i - 1, 0));
            } else if (e.key === "Enter" && open && active >= 0) {
              e.preventDefault();
              choose(results[active]);
            } else if (e.key === "Escape") {
              setOpen(false);
            }
          }}
        />
        {loading && <span className="combobox-spinner" aria-hidden="true" />}
      </div>
      {open && query.trim().length >= 3 && (
        <ul id="address-search-list" role="listbox" className="combobox-list">
          {results.map((place, index) => (
            <li
              key={`${place.latitude},${place.longitude},${index}`}
              id={`address-option-${index}`}
              role="option"
              aria-selected={index === active}
              className={index === active ? "active" : ""}
              onMouseDown={(e) => {
                e.preventDefault();
                choose(place);
              }}
              onMouseEnter={() => setActive(index)}
            >
              <span className="option-main">{[place.street, place.houseNumber].filter(Boolean).join(", ") || place.label}</span>
              <span className="option-sub">{[place.district, place.city, place.region].filter(Boolean).join(", ")}</span>
            </li>
          ))}
          {!loading && results.length === 0 && <li className="combobox-empty">{t("propertyForm.search.empty")}</li>}
        </ul>
      )}
    </div>
  );
}
