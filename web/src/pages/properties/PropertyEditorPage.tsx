import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../auth/AuthContext";
import { api, ApiError } from "../../api/client";
import type { FormSchema, MediaView, PropertyDetails, PropertyPayload, PropertyStatus, PropertyType } from "../../api/types";
import { ChipGroup, CountChips, Field, NumberInput, ToggleChips } from "../../components/form/Field";
import { StatusPill } from "../../components/StatusPill";
import { AddressSection, type AddressValues } from "./AddressSection";
import { MediaSection } from "./MediaSection";
import { PropertyTabs } from "./PropertyTabs";
import {
  COMMERCIAL_TYPES, CONDITIONS, CURRENCIES, FEATURE_GROUPS, FEATURE_GROUPS_BY_TYPE, HEATING, LAND_PURPOSES, MARKETS,
  PROPERTY_TYPES, SECTIONS, SQM_PER_SOTKA, VISIBLE_FIELDS, WALL_MATERIALS, sectionOf, type SectionId,
} from "./propertyFields";

const AUTOSAVE_DELAY_MS = 1200;
const LAST_TYPE_KEY = "memphisreo-last-property-type";
const LAST_PLACE_KEY = "memphisreo-last-place";
const EMPTY_ADDRESS: AddressValues = {
  city: "", district: "", region: "", street: "", houseNumber: "", postalCode: "", complexName: "",
  latitude: null, longitude: null, geocodeSource: null,
};

type Values = Record<string, string>;
interface EditorState {
  type: PropertyType;
  values: Values;
  features: string[];
  address: AddressValues;
}
type SaveState = "idle" | "dirty" | "saving" | "saved" | "error";

export function PropertyEditorPage() {
  const { t, i18n } = useTranslation();
  const { token } = useAuth();
  const { propertyId: routeId } = useParams();

  const [schema, setSchema] = useState<FormSchema | null>(null);
  const [id, setId] = useState<string | null>(routeId ?? null);
  const [status, setStatus] = useState<PropertyStatus>("DRAFT");
  const [state, setState] = useState<EditorState>(() => ({
    type: (readLastType() ?? "APARTMENT") as PropertyType,
    values: { "price.currency": "USD" },
    features: [],
    // Новий об'єкт — у тому ж місті/області, що й попередній; мапа центрується там само.
    address: routeId ? EMPTY_ADDRESS : { ...EMPTY_ADDRESS, ...readLastPlace() },
  }));
  const [media, setMedia] = useState<MediaView[]>([]);
  const [loaded, setLoaded] = useState(!routeId);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({});
  const [showRequired, setShowRequired] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [toast, setToast] = useState<{ message: string; action?: { label: string; run: () => void } } | null>(null);
  const [activeSection, setActiveSection] = useState<SectionId>("basics");

  const stateRef = useRef(state);
  stateRef.current = state;
  const idRef = useRef(id);
  idRef.current = id;
  const savingRef = useRef<Promise<string | null> | null>(null);
  const dirtyRef = useRef(false);

  // ---------- Завантаження ----------
  useEffect(() => {
    if (!token) return;
    api.get<FormSchema>("/api/properties/form-schema", token).then(setSchema).catch(() => undefined);
  }, [token]);

  useEffect(() => {
    if (!token || !routeId) return;
    api
      .get<PropertyDetails>(`/api/properties/${routeId}`, token)
      .then((details) => {
        setState(fromDetails(details));
        setMedia(details.media);
        setStatus(details.status);
        setSavedAt(new Date(details.updatedAt));
        setSaveState("saved");
        setLoaded(true);
      })
      .catch((err) => setLoadError(err instanceof ApiError && err.status === 404 ? t("propertyForm.notFound") : t("propertyForm.loadError")));
  }, [routeId, token, t]);

  // ---------- Збереження ----------
  const save = useCallback(async (): Promise<string | null> => {
    if (!token) return null;
    if (savingRef.current) {
      await savingRef.current;
      if (!dirtyRef.current) return idRef.current;
    }
    dirtyRef.current = false;
    const payload = toPayload(stateRef.current);
    setSaveState("saving");
    const run = (async () => {
      try {
        const details = idRef.current
          ? await api.put<PropertyDetails>(`/api/properties/${idRef.current}`, payload, token)
          : await api.post<PropertyDetails>("/api/properties", payload, token);
        if (!idRef.current) {
          idRef.current = details.id;
          setId(details.id);
          // URL оновлюємо без переходу роутера: перемонтування скинуло б фокус і завантаження.
          window.history.replaceState(window.history.state, "", `/properties/${details.id}`);
        }
        setStatus(details.status);
        setServerErrors({});
        setSavedAt(new Date());
        rememberPlace(stateRef.current.address);
        setSaveState(dirtyRef.current ? "dirty" : "saved");
        return details.id;
      } catch (err) {
        if (err instanceof ApiError && err.errors.length) {
          setServerErrors(Object.fromEntries(err.errors.map((e) => [e.field, e.code])));
        }
        setSaveState("error");
        return null;
      } finally {
        savingRef.current = null;
      }
    })();
    savingRef.current = run;
    return run;
  }, [token]);

  // Автозбереження: 1.2 с після останньої зміни.
  useEffect(() => {
    if (saveState !== "dirty") return;
    const timer = window.setTimeout(() => void save(), AUTOSAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [state, saveState, save]);

  // Ctrl/Cmd+S — зберегти зараз; попередження при виході з незбереженими змінами.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "s") {
        e.preventDefault();
        void save();
      }
    };
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (dirtyRef.current || savingRef.current) e.preventDefault();
    };
    window.addEventListener("keydown", onKey);
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("beforeunload", onBeforeUnload);
    };
  }, [save]);

  function update(mutator: (s: EditorState) => EditorState) {
    dirtyRef.current = true;
    setState((s) => mutator(s));
    setSaveState("dirty");
  }

  const setValue = (key: string) => (value: string) => {
    update((s) => ({ ...s, values: { ...s.values, [key]: value } }));
    setServerErrors((errors) => (errors[key] ? omit(errors, key) : errors));
  };

  const ensurePropertyId = useCallback(async () => {
    if (idRef.current) return idRef.current;
    dirtyRef.current = true;
    const saved = await save();
    if (!saved) throw new Error("save failed");
    return saved;
  }, [save]);

  async function complete() {
    setShowRequired(true);
    if (missing.length) {
      scrollToField(missing[0]);
      return;
    }
    setCompleting(true);
    const savedId = await save();
    if (!savedId) {
      setCompleting(false);
      return;
    }
    try {
      const details = await api.post<PropertyDetails>(`/api/properties/${savedId}/complete`, {}, token!);
      setStatus(details.status);
      setToast({ message: t("propertyForm.completed"), action: { label: t("propertyTabs.sale"), run: () => (window.location.href = `/properties/${savedId}/sale`) } });
    } catch (err) {
      if (err instanceof ApiError && err.errors.length) {
        setServerErrors(Object.fromEntries(err.errors.map((e) => [e.field, e.code])));
        scrollToField(err.errors[0].field);
      }
    } finally {
      setCompleting(false);
    }
  }

  // ---------- Похідні дані ----------
  const visible = useMemo(() => new Set(VISIBLE_FIELDS[state.type]), [state.type]);
  const requiredFields = useMemo(() => [...(schema?.required[state.type] ?? []), "price.amount"], [schema, state.type]);
  const isRequired = (field: string) => requiredFields.includes(field);
  const missing = requiredFields.filter((field) => !isFilled(state, field));
  const completeness = requiredFields.length ? Math.round(((requiredFields.length - missing.length) / requiredFields.length) * 100) : 0;
  const photoCount = media.filter((m) => m.kind === "PHOTO").length;
  const recommendations = [
    state.address.latitude === null ? "location" : null,
    (state.values.description ?? "").trim().length < 100 ? "description" : null,
    photoCount < (schema?.recommendedPhotos ?? 5) ? "photos" : null,
  ].filter(Boolean) as string[];

  const errorFor = (field: string): string | null => {
    const code = serverErrors[field] ?? (showRequired && missing.includes(field) ? "required" : undefined);
    return code ? t(`propertyForm.errors.${code}`, { defaultValue: t("propertyForm.errors.invalid") }) : null;
  };

  const sectionStatus = (section: SectionId): "done" | "error" | "todo" | "neutral" => {
    if (Object.keys(serverErrors).some((f) => sectionOf(f) === section)) return "error";
    const sectionRequired = requiredFields.filter((f) => sectionOf(f) === section);
    if (!sectionRequired.length) return section === "media" && photoCount > 0 ? "done" : "neutral";
    const sectionMissing = sectionRequired.filter((f) => missing.includes(f));
    if (sectionMissing.length && showRequired) return "error";
    return sectionMissing.length ? "todo" : "done";
  };

  // Підсвічування поточної секції в навігації під час прокрутки.
  useEffect(() => {
    if (!loaded) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visibleEntry = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
        if (visibleEntry) setActiveSection(visibleEntry.target.id.replace("section-", "") as SectionId);
      },
      { rootMargin: "-80px 0px -60% 0px" },
    );
    SECTIONS.forEach((s) => {
      const el = document.getElementById(`section-${s}`);
      if (el) observer.observe(el);
    });
    return () => observer.disconnect();
  }, [loaded]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), toast.action ? 5000 : 3500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  if (loadError) {
    return (
      <div className="page">
        <p className="error">{loadError}</p>
        <Link to="/properties" className="back-link">
          ← {t("propertyForm.backToList")}
        </Link>
      </div>
    );
  }
  if (!loaded || !token) return <div className="page" />;

  // ---------- Рендер полів ----------
  const v = state.values;
  const show = (field: string) => visible.has(field);
  const num = (field: string, label: string, unit?: string, opts: { decimal?: boolean; hint?: string; placeholder?: string } = {}) =>
    show(field) && (
      <Field label={label} required={isRequired(field)} error={errorFor(field)} hint={opts.hint} fieldKey={field}>
        {(controlId, describedBy) => (
          <NumberInput
            id={controlId}
            value={v[field] ?? ""}
            onChange={setValue(field)}
            unit={unit}
            decimal={opts.decimal}
            placeholder={opts.placeholder}
            describedBy={describedBy}
            invalid={!!errorFor(field)}
            required={isRequired(field)}
          />
        )}
      </Field>
    );
  const select = (field: string, label: string, options: readonly string[], prefix: string) =>
    show(field) && (
      <Field label={label} required={isRequired(field)} error={errorFor(field)} fieldKey={field}>
        {(controlId, describedBy) => (
          <select id={controlId} value={v[field] ?? ""} onChange={(e) => setValue(field)(e.target.value)} aria-describedby={describedBy} aria-invalid={!!errorFor(field) || undefined}>
            <option value="">{t("propertyForm.notSpecified")}</option>
            {options.map((o) => (
              <option key={o} value={o}>
                {t(`${prefix}.${o}`)}
              </option>
            ))}
          </select>
        )}
      </Field>
    );
  const count = (field: string, label: string, max: number, min = 1) =>
    show(field) && (
      <Field label={label} required={isRequired(field)} error={errorFor(field)} fieldKey={field} wide>
        {(controlId, describedBy) => <CountChips id={controlId} label={label} value={v[field] ?? ""} onChange={setValue(field)} max={max} min={min} describedBy={describedBy} />}
      </Field>
    );

  const landUnit = t("propertyForm.units.sotka");
  const area = parseNumber(v.areaSqm);
  const land = parseNumber(v.landAreaSqm);
  const price = parseNumber(v["price.amount"]);
  const currency = v["price.currency"] || "USD";
  const priceFormat = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 0 });
  const pricePerUnit =
    price && state.type === "LAND" && land ? `${priceFormat.format(price / land)} ${currency} / ${landUnit}` : price && area ? `${priceFormat.format(price / area)} ${currency} / м²` : null;

  const featureGroups = FEATURE_GROUPS_BY_TYPE[state.type];

  return (
    <div className="editor">
      <header className="editor-header">
        <div className="editor-title">
          <Link to="/properties" className="back-link">
            ← {t("propertyForm.backToList")}
          </Link>
          <h1>{id ? t("propertyForm.titleEdit") : t("propertyForm.titleNew")}</h1>
          <StatusPill status={status} />
          {id && <PropertyTabs propertyId={id} />}
        </div>
        <div className="editor-actions">
          <SaveIndicator state={saveState} savedAt={savedAt} onRetry={() => void save()} />
          {status === "DRAFT" ? (
            <button type="button" onClick={complete} disabled={completing}>
              {completing ? t("propertyForm.completing") : t("propertyForm.complete")}
            </button>
          ) : (
            <span className="editor-done">✓ {t("propertyForm.active")}</span>
          )}
        </div>
      </header>

      <div className="editor-body">
        <nav className="editor-nav" aria-label={t("propertyForm.sectionsNav")}>
          <div className="completeness">
            <div className="completeness-row">
              <span>{t("propertyForm.completeness")}</span>
              <strong>{completeness}%</strong>
            </div>
            <div className="completeness-bar" role="progressbar" aria-valuenow={completeness} aria-valuemin={0} aria-valuemax={100}>
              <span style={{ width: `${completeness}%` }} />
            </div>
          </div>
          <ol className="section-list">
            {SECTIONS.map((section) => (
              <li key={section}>
                <a
                  href={`#section-${section}`}
                  className={`section-link status-${sectionStatus(section)}${activeSection === section ? " current" : ""}`}
                  onClick={(e) => {
                    e.preventDefault();
                    document.getElementById(`section-${section}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
                  }}
                >
                  <span className="section-dot" aria-hidden="true" />
                  {t(`propertyForm.sections.${section}`)}
                </a>
              </li>
            ))}
          </ol>
          {missing.length > 0 && (
            <div className="checklist">
              <p className="checklist-title">{t("propertyForm.missingTitle", { count: missing.length })}</p>
              <ul>
                {missing.map((field) => (
                  <li key={field}>
                    <button type="button" className="link-button" onClick={() => scrollToField(field)}>
                      {t(`propertyForm.fieldNames.${field}`)}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {recommendations.length > 0 && (
            <div className="checklist checklist-soft">
              <p className="checklist-title">{t("propertyForm.recommendTitle")}</p>
              <ul>
                {recommendations.map((r) => (
                  <li key={r}>
                    <button type="button" className="link-button" onClick={() => scrollToField(r)}>
                      {t(`propertyForm.recommend.${r}`, { count: schema?.recommendedPhotos ?? 5 })}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </nav>

        <div className="editor-sections">
          <Section id="basics" title={t("propertyForm.sections.basics")}>
            <Field label={t("propertyForm.fields.type")} required fieldKey="type" wide>
              {() => (
                <ChipGroup
                  label={t("propertyForm.fields.type")}
                  allowClear={false}
                  value={state.type}
                  options={PROPERTY_TYPES.map((type) => ({ value: type, label: t(`propertyTypes.${type}`) }))}
                  onChange={(type) => {
                    try {
                      localStorage.setItem(LAST_TYPE_KEY, type);
                    } catch {
                      // не критично
                    }
                    update((s) => ({ ...s, type: type as PropertyType }));
                  }}
                />
              )}
            </Field>
            {show("market") && (
              <Field label={t("propertyForm.fields.market")} required={isRequired("market")} error={errorFor("market")} fieldKey="market" wide>
                {() => (
                  <ChipGroup
                    label={t("propertyForm.fields.market")}
                    value={v.market ?? ""}
                    options={MARKETS.map((m) => ({ value: m, label: t(`propertyForm.options.market.${m}`) }))}
                    onChange={setValue("market")}
                  />
                )}
              </Field>
            )}
            <div className="form-grid">
              {select("commercialType", t("propertyForm.fields.commercialType"), COMMERCIAL_TYPES, "propertyForm.options.commercialType")}
              {select("landPurpose", t("propertyForm.fields.landPurpose"), LAND_PURPOSES, "propertyForm.options.landPurpose")}
            </div>
          </Section>

          <Section id="location" title={t("propertyForm.sections.location")} description={t("propertyForm.sectionHints.location")}>
            <AddressSection
              token={token}
              value={state.address}
              onChange={(patch) => {
                update((s) => ({ ...s, address: { ...s.address, ...patch } }));
                setServerErrors((errors) => Object.fromEntries(Object.entries(errors).filter(([f]) => !f.startsWith("address.") && f !== "location")));
              }}
              unitNumber={v.unitNumber ?? ""}
              onUnitNumberChange={setValue("unitNumber")}
              showUnit={show("unitNumber")}
              showComplex={show("address.complexName")}
              required={isRequired}
              error={errorFor}
            />
          </Section>

          <Section id="parameters" title={t("propertyForm.sections.parameters")}>
            <div className="form-grid">
              {num("areaSqm", t("propertyForm.fields.areaSqm"), "м²", { decimal: true })}
              {num("livingAreaSqm", t("propertyForm.fields.livingAreaSqm"), "м²", { decimal: true })}
              {num("kitchenAreaSqm", t("propertyForm.fields.kitchenAreaSqm"), "м²", { decimal: true })}
              {num("landAreaSqm", t("propertyForm.fields.landAreaSqm"), landUnit, { decimal: true, hint: t("propertyForm.hints.sotka") })}
            </div>
            {count("rooms", state.type === "COMMERCIAL" ? t("propertyForm.fields.premises") : t("propertyForm.fields.rooms"), 5)}
            {count("bedrooms", t("propertyForm.fields.bedrooms"), 5)}
            {count("bathrooms", t("propertyForm.fields.bathrooms"), 3)}
            <div className="form-grid">
              {num("floor", t("propertyForm.fields.floor"), undefined, { hint: t("propertyForm.hints.floor") })}
              {num("totalFloors", t("propertyForm.fields.totalFloors"))}
              {num("yearBuilt", t("propertyForm.fields.yearBuilt"), undefined, { placeholder: "2015" })}
              {num("ceilingHeightM", t("propertyForm.fields.ceilingHeightM"), "м", { decimal: true, placeholder: "2,7" })}
              {select("wallMaterial", t("propertyForm.fields.wallMaterial"), WALL_MATERIALS, "propertyForm.options.wallMaterial")}
              {select("condition", t("propertyForm.fields.condition"), CONDITIONS, "propertyForm.options.condition")}
              {select("heating", t("propertyForm.fields.heating"), HEATING, "propertyForm.options.heating")}
              {num("parkingSpaces", t("propertyForm.fields.parkingSpaces"))}
              {show("cadastralNumber") && (
                <Field label={t("propertyForm.fields.cadastralNumber")} error={errorFor("cadastralNumber")} hint={t("propertyForm.hints.cadastral")} fieldKey="cadastralNumber">
                  {(controlId, describedBy) => (
                    <input
                      id={controlId}
                      className="mono-input"
                      value={v.cadastralNumber ?? ""}
                      placeholder="3222486200:03:001:0001"
                      aria-describedby={describedBy}
                      onChange={(e) => setValue("cadastralNumber")(e.target.value)}
                    />
                  )}
                </Field>
              )}
            </div>
            {show("hasElevator") && (
              <Field label={t("propertyForm.fields.hasElevator")} fieldKey="hasElevator" wide>
                {() => (
                  <ChipGroup
                    label={t("propertyForm.fields.hasElevator")}
                    value={v.hasElevator ?? ""}
                    options={[
                      { value: "true", label: t("propertyForm.yes") },
                      { value: "false", label: t("propertyForm.no") },
                    ]}
                    onChange={setValue("hasElevator")}
                  />
                )}
              </Field>
            )}
          </Section>

          <Section id="features" title={t("propertyForm.sections.features")}>
            {featureGroups.map((group) => (
              <div key={group} className="feature-group">
                <h3 className="form-section">{t(`propertyForm.featureGroups.${group}`)}</h3>
                <ToggleChips
                  options={FEATURE_GROUPS[group].map((f) => ({ value: f, label: t(`propertyForm.features.${f}`) }))}
                  selected={state.features}
                  onToggle={(feature) =>
                    update((s) => ({
                      ...s,
                      features: s.features.includes(feature) ? s.features.filter((f) => f !== feature) : [...s.features, feature],
                    }))
                  }
                />
              </div>
            ))}
          </Section>

          <Section id="price" title={t("propertyForm.sections.price")}>
            <div className="price-row">
              <Field label={t("propertyForm.fields.price")} required error={errorFor("price.amount")} fieldKey="price.amount">
                {(controlId, describedBy) => (
                  <NumberInput
                    id={controlId}
                    value={v["price.amount"] ?? ""}
                    onChange={setValue("price.amount")}
                    onBlur={() => {
                      const formatted = formatThousands(v["price.amount"] ?? "");
                      if (formatted !== (v["price.amount"] ?? "")) setValue("price.amount")(formatted);
                    }}
                    unit={currency}
                    decimal
                    describedBy={describedBy}
                    invalid={!!errorFor("price.amount")}
                    required
                  />
                )}
              </Field>
              <Field label={t("propertyForm.fields.currency")} fieldKey="price.currency" error={errorFor("price.currency")}>
                {() => (
                  <ChipGroup
                    label={t("propertyForm.fields.currency")}
                    allowClear={false}
                    value={currency}
                    options={CURRENCIES.map((c) => ({ value: c, label: c }))}
                    onChange={setValue("price.currency")}
                  />
                )}
              </Field>
            </div>
            {pricePerUnit && <p className="hint">≈ {pricePerUnit}</p>}
          </Section>

          <Section id="description" title={t("propertyForm.sections.description")}>
            <Field label={t("propertyForm.fields.title")} error={errorFor("title")} hint={t("propertyForm.hints.title")} fieldKey="title" wide>
              {(controlId, describedBy) => (
                <input id={controlId} maxLength={150} value={v.title ?? ""} aria-describedby={describedBy} onChange={(e) => setValue("title")(e.target.value)} />
              )}
            </Field>
            <Field
              label={t("propertyForm.fields.description")}
              error={errorFor("description")}
              hint={t("propertyForm.hints.description", { count: (v.description ?? "").length })}
              fieldKey="description"
              wide
            >
              {(controlId, describedBy) => (
                <textarea
                  id={controlId}
                  rows={7}
                  maxLength={5000}
                  value={v.description ?? ""}
                  aria-describedby={describedBy}
                  onChange={(e) => setValue("description")(e.target.value)}
                />
              )}
            </Field>
          </Section>

          <Section id="media" title={t("propertyForm.sections.media")} description={t("propertyForm.sectionHints.media")}>
            <div data-field="photos">
              <MediaSection
                token={token}
                propertyId={id}
                ensurePropertyId={ensurePropertyId}
                media={media}
                onMediaChange={setMedia}
                recommendedPhotos={schema?.recommendedPhotos ?? 5}
                notify={(message, action) => setToast({ message, action })}
              />
            </div>
          </Section>
        </div>
      </div>

      <div className="editor-mobile-bar">
        <SaveIndicator state={saveState} savedAt={savedAt} onRetry={() => void save()} />
        <span className="mobile-completeness">{completeness}%</span>
        {status === "DRAFT" && (
          <button type="button" onClick={complete} disabled={completing}>
            {t("propertyForm.complete")}
          </button>
        )}
      </div>

      {toast && (
        <div className="toast" role="status">
          <span>{toast.message}</span>
          {toast.action && (
            <button
              type="button"
              className="link-button"
              onClick={() => {
                toast.action!.run();
                setToast(null);
              }}
            >
              {toast.action.label}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function Section({ id, title, description, children }: { id: SectionId; title: string; description?: string; children: ReactNode }) {
  return (
    <section id={`section-${id}`} className="editor-section" aria-labelledby={`section-${id}-title`}>
      <h2 id={`section-${id}-title`}>{title}</h2>
      {description && <p className="section-description">{description}</p>}
      {children}
    </section>
  );
}

function SaveIndicator({ state, savedAt, onRetry }: { state: SaveState; savedAt: Date | null; onRetry: () => void }) {
  const { t, i18n } = useTranslation();
  const time = savedAt ? new Intl.DateTimeFormat(i18n.language, { hour: "2-digit", minute: "2-digit" }).format(savedAt) : "";
  if (state === "saving") return <span className="save-indicator saving">{t("propertyForm.save.saving")}</span>;
  if (state === "dirty") return <span className="save-indicator">{t("propertyForm.save.dirty")}</span>;
  if (state === "error")
    return (
      <span className="save-indicator save-error">
        {t("propertyForm.save.error")}{" "}
        <button type="button" className="link-button" onClick={onRetry}>
          {t("propertyForm.save.retry")}
        </button>
      </span>
    );
  if (state === "saved") return <span className="save-indicator saved">✓ {t("propertyForm.save.saved", { time })}</span>;
  return <span className="save-indicator">{t("propertyForm.save.idle")}</span>;
}

// ---------- Перетворення стану ↔ API ----------

const NUMBER_FIELDS = [
  "areaSqm", "livingAreaSqm", "kitchenAreaSqm", "rooms", "bedrooms", "bathrooms", "floor", "totalFloors", "yearBuilt",
  "ceilingHeightM", "parkingSpaces",
];
const TEXT_FIELDS = ["market", "wallMaterial", "condition", "heating", "landPurpose", "commercialType", "cadastralNumber", "unitNumber"];

function fromDetails(details: PropertyDetails): EditorState {
  const p = details.property;
  const values: Values = { "price.currency": details.price?.currency ?? "USD" };
  const record = p as unknown as Record<string, unknown>;
  for (const field of [...NUMBER_FIELDS, ...TEXT_FIELDS]) {
    const value = record[field];
    if (value !== null && value !== undefined) values[field] = String(value).replace(".", ",");
  }
  for (const field of TEXT_FIELDS) {
    if (record[field] !== null && record[field] !== undefined) values[field] = String(record[field]);
  }
  if (p.landAreaSqm !== null) values.landAreaSqm = String(p.landAreaSqm / SQM_PER_SOTKA).replace(".", ",");
  if (p.hasElevator !== null) values.hasElevator = String(p.hasElevator);
  if (p.title) values.title = p.title;
  if (p.description) values.description = p.description;
  if (details.price?.amount != null) values["price.amount"] = formatThousands(String(details.price.amount));
  return {
    type: p.type,
    values,
    features: p.features ?? [],
    address: {
      city: p.address.city ?? "",
      district: p.address.district ?? "",
      region: p.address.region ?? "",
      street: p.address.street ?? "",
      houseNumber: p.address.houseNumber ?? "",
      postalCode: p.address.postalCode ?? "",
      complexName: p.address.complexName ?? "",
      latitude: p.address.latitude,
      longitude: p.address.longitude,
      geocodeSource: p.address.geocodeSource,
    },
  };
}

/** Невидимі для типу поля не надсилаються (null), але лишаються в стані — перемикання типу назад їх не губить. */
function toPayload(state: EditorState): PropertyPayload {
  const visible = new Set(VISIBLE_FIELDS[state.type]);
  const v = state.values;
  const number = (field: string) => (visible.has(field) ? parseNumber(v[field]) : null);
  const text = (field: string) => (visible.has(field) && v[field]?.trim() ? v[field].trim() : null);
  const allowedFeatures = new Set<string>(FEATURE_GROUPS_BY_TYPE[state.type].flatMap((g) => [...FEATURE_GROUPS[g]]));
  const land = number("landAreaSqm");
  const a = state.address;
  return {
    property: {
      type: state.type,
      market: text("market"),
      title: v.title?.trim() || null,
      description: v.description?.trim() || null,
      areaSqm: number("areaSqm"),
      livingAreaSqm: number("livingAreaSqm"),
      kitchenAreaSqm: number("kitchenAreaSqm"),
      landAreaSqm: land === null ? null : Math.round(land * SQM_PER_SOTKA * 100) / 100,
      rooms: number("rooms"),
      bedrooms: number("bedrooms"),
      bathrooms: number("bathrooms"),
      floor: number("floor"),
      totalFloors: number("totalFloors"),
      yearBuilt: number("yearBuilt"),
      ceilingHeightM: number("ceilingHeightM"),
      wallMaterial: text("wallMaterial"),
      condition: text("condition"),
      heating: text("heating"),
      landPurpose: text("landPurpose"),
      commercialType: text("commercialType"),
      cadastralNumber: text("cadastralNumber"),
      hasElevator: visible.has("hasElevator") && v.hasElevator ? v.hasElevator === "true" : null,
      parkingSpaces: number("parkingSpaces"),
      features: state.features.filter((f) => allowedFeatures.has(f)),
      unitNumber: text("unitNumber"),
      address: {
        countryCode: "UA",
        region: a.region || null,
        city: a.city || null,
        district: a.district || null,
        street: a.street || null,
        houseNumber: a.houseNumber || null,
        postalCode: a.postalCode || null,
        complexName: visible.has("address.complexName") ? a.complexName || null : null,
        latitude: a.latitude,
        longitude: a.longitude,
        geocodeSource: a.geocodeSource,
      },
    },
    price: { amount: parseNumber(v["price.amount"]), currency: v["price.currency"] || "USD" },
  };
}

function isFilled(state: EditorState, field: string): boolean {
  if (field.startsWith("address.")) {
    const key = field.slice("address.".length) as keyof AddressValues;
    return String(state.address[key] ?? "").trim() !== "";
  }
  return (state.values[field] ?? "").trim() !== "";
}

function parseNumber(raw: string | undefined): number | null {
  if (!raw) return null;
  const normalized = raw.replace(/\s/g, "").replace(",", ".");
  if (normalized === "" || normalized === ".") return null;
  const value = Number(normalized);
  return Number.isFinite(value) ? value : null;
}

function formatThousands(raw: string): string {
  const value = parseNumber(raw);
  return value === null ? raw : new Intl.NumberFormat("uk-UA", { maximumFractionDigits: 2 }).format(value).replace(/ /g, " ");
}

function scrollToField(field: string) {
  const target = document.querySelector<HTMLElement>(`[data-field="${field}"]`) ?? document.getElementById(`section-${sectionOf(field)}`);
  target?.scrollIntoView({ behavior: "smooth", block: "center" });
  const control = target?.querySelector<HTMLElement>("input, select, textarea, button");
  window.setTimeout(() => control?.focus({ preventScroll: true }), 350);
}

function omit(record: Record<string, string>, key: string) {
  const copy = { ...record };
  delete copy[key];
  return copy;
}

/** Останнє місто агента: місто, район, область і центр мапи (без вулиці/будинку). */
function readLastPlace(): Partial<AddressValues> {
  try {
    const raw = localStorage.getItem(LAST_PLACE_KEY);
    if (!raw) return {};
    const place = JSON.parse(raw) as { city?: string; region?: string; latitude?: number; longitude?: number };
    return { city: place.city ?? "", region: place.region ?? "", mapCenter: place.latitude != null && place.longitude != null ? [place.latitude, place.longitude] : null };
  } catch {
    return {};
  }
}

function rememberPlace(address: AddressValues) {
  if (!address.city) return;
  try {
    localStorage.setItem(LAST_PLACE_KEY, JSON.stringify({ city: address.city, region: address.region, latitude: address.latitude, longitude: address.longitude }));
  } catch {
    // сховище недоступне — просто не запам'ятовуємо
  }
}

function readLastType(): string | null {
  try {
    return localStorage.getItem(LAST_TYPE_KEY);
  } catch {
    return null;
  }
}
