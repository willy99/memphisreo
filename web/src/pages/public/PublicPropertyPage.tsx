import { lazy, Suspense, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, ApiError } from "../../api/client";
import type { PublicDetails } from "../../api/types";
import { Lightbox, embedUrl } from "../properties/Lightbox";
import { addressLine, facts, money, pricePerUnit } from "./publicFormat";

const PublicMap = lazy(() => import("./PublicMap"));

/** Публічна сторінка об'єкта: галерея → ціна → факти → опис → зручності → мапа → агент/заявка → схожі. */
export function PublicPropertyPage() {
  const { t, i18n } = useTranslation();
  const { slug, propertyId } = useParams();
  const [details, setDetails] = useState<PublicDetails | null>(null);
  const [missing, setMissing] = useState(false);
  const [lightbox, setLightbox] = useState<number | null>(null);

  useEffect(() => {
    if (!slug || !propertyId) return;
    setDetails(null);
    api.get<PublicDetails>(`/api/public/agencies/${slug}/properties/${propertyId}`, "").then(setDetails).catch(() => setMissing(true));
    window.scrollTo({ top: 0 });
  }, [slug, propertyId]);

  useEffect(() => {
    if (details) document.title = `${details.title ?? t(`propertyTypes.${details.type}`)} — ${details.agency.name}`;
    return () => {
      document.title = "Memphis";
    };
  }, [details, t]);

  if (missing)
    return (
      <div className="empty-state">
        <h2>{t("public.propertyNotFound")}</h2>
        <Link to={`/p/${slug}`} className="button-link">
          {t("public.backToList")}
        </Link>
      </div>
    );
  if (!details) return <div className="page" />;

  const d = details;
  const sotka = t("propertyForm.units.sotka");
  const photos = d.photos.filter((p) => p.url);
  const lead = d.agents[0];
  const keyFacts: [string, string | null][] = [
    [t("propertyForm.fields.rooms"), d.rooms?.toString() ?? null],
    [t("propertyForm.fields.areaSqm"), d.areaSqm ? `${d.areaSqm} м²` : null],
    [t("propertyForm.fields.livingAreaSqm"), d.livingAreaSqm ? `${d.livingAreaSqm} м²` : null],
    [t("propertyForm.fields.kitchenAreaSqm"), d.kitchenAreaSqm ? `${d.kitchenAreaSqm} м²` : null],
    [t("propertyForm.fields.landAreaSqm"), d.landAreaSqm ? `${+(d.landAreaSqm / 100).toFixed(2)} ${sotka}` : null],
    [t("propertyForm.fields.floor"), d.floor != null && d.totalFloors ? `${d.floor} / ${d.totalFloors}` : d.totalFloors?.toString() ?? null],
    [t("propertyForm.fields.bedrooms"), d.bedrooms?.toString() ?? null],
    [t("propertyForm.fields.bathrooms"), d.bathrooms?.toString() ?? null],
    [t("propertyForm.fields.yearBuilt"), d.yearBuilt?.toString() ?? null],
    [t("propertyForm.fields.ceilingHeightM"), d.ceilingHeightM ? `${d.ceilingHeightM} м` : null],
    [t("propertyForm.fields.market"), d.market ? t(`propertyForm.options.market.${d.market}`) : null],
    [t("propertyForm.fields.wallMaterial"), d.wallMaterial ? t(`propertyForm.options.wallMaterial.${d.wallMaterial}`) : null],
    [t("propertyForm.fields.condition"), d.condition ? t(`propertyForm.options.condition.${d.condition}`) : null],
    [t("propertyForm.fields.heating"), d.heating ? t(`propertyForm.options.heating.${d.heating}`) : null],
    [t("propertyForm.fields.landPurpose"), d.landPurpose ? t(`propertyForm.options.landPurpose.${d.landPurpose}`) : null],
    [t("propertyForm.fields.commercialType"), d.commercialType ? t(`propertyForm.options.commercialType.${d.commercialType}`) : null],
    [t("propertyForm.fields.hasElevator"), d.hasElevator == null ? null : d.hasElevator ? t("propertyForm.yes") : t("propertyForm.no")],
    [t("propertyForm.fields.parkingSpaces"), d.parkingSpaces ? String(d.parkingSpaces) : null],
  ];

  return (
    <article className="public-property">
      <nav className="breadcrumbs" aria-label="breadcrumbs">
        <Link to={`/p/${slug}`}>{d.agency.name}</Link> · <Link to={`/p/${slug}?type=${d.type}`}>{t(`propertyTypes.${d.type}`)}</Link>
        {d.district && (
          <>
            {" "}
            · <Link to={`/p/${slug}?district=${encodeURIComponent(d.district)}`}>{d.district}</Link>
          </>
        )}
      </nav>

      {photos.length > 0 ? (
        <div className={`gallery gallery-${Math.min(photos.length, 3)}`}>
          {photos.slice(0, 3).map((p, i) => (
            <button key={i} type="button" className="gallery-item" onClick={() => setLightbox(i)}>
              <img src={p.url!} alt={p.caption ?? ""} loading={i === 0 ? "eager" : "lazy"} />
              {i === 2 && photos.length > 3 && <span className="gallery-more">+{photos.length - 3}</span>}
            </button>
          ))}
        </div>
      ) : (
        <div className="gallery-empty">{t("properties.noPhoto")}</div>
      )}

      <div className="public-body">
        <div className="public-content">
          <h1>{d.title ?? t(`propertyTypes.${d.type}`)}</h1>
          <p className="public-address">
            {addressLine(d) || d.city}
            {d.approximateLocation && <span className="hint"> · {t("public.approximate")}</span>}
          </p>

          <div className="price-block">
            <span className="price-main">{money(d.price, d.currency, i18n.language)}</span>
            {pricePerUnit(d, i18n.language, sotka) && <span className="hint">{pricePerUnit(d, i18n.language, sotka)}</span>}
          </div>

          <p className="facts-line">{[t(`propertyTypes.${d.type}`), ...facts(d, t)].join(" · ")}</p>

          <section>
            <h2>{t("public.sections.facts")}</h2>
            <dl className="facts-grid">
              {keyFacts
                .filter(([, v]) => v)
                .map(([k, v]) => (
                  <div key={k}>
                    <dt>{k}</dt>
                    <dd>{v}</dd>
                  </div>
                ))}
            </dl>
          </section>

          {d.description && (
            <section>
              <h2>{t("public.sections.description")}</h2>
              <p className="public-description">{d.description}</p>
            </section>
          )}

          {d.features.length > 0 && (
            <section>
              <h2>{t("public.sections.features")}</h2>
              <ul className="feature-chips">
                {d.features.map((f) => (
                  <li key={f}>✓ {t(`propertyForm.features.${f}`)}</li>
                ))}
              </ul>
            </section>
          )}

          {d.floorplans.length > 0 && (
            <section>
              <h2>{t("propertyForm.media.tabs.FLOORPLAN")}</h2>
              <div className="floorplans">
                {d.floorplans.map((p, i) => (
                  <a key={i} href={p.url ?? "#"} target="_blank" rel="noreferrer">
                    <img src={p.thumbUrl ?? p.url ?? ""} alt={p.caption ?? ""} loading="lazy" />
                  </a>
                ))}
              </div>
            </section>
          )}

          {d.videoUrls.map((v) => {
            const embed = embedUrl(v);
            return embed ? (
              <section key={v}>
                <h2>{t("propertyForm.media.tabs.VIDEO")}</h2>
                <iframe className="public-video" src={embed} title="video" allow="autoplay; encrypted-media; fullscreen" referrerPolicy="strict-origin-when-cross-origin" />
              </section>
            ) : (
              <section key={v}>
                <h2>{t("propertyForm.media.tabs.VIDEO")}</h2>
                <video className="public-video" src={v} controls preload="metadata" playsInline />
              </section>
            );
          })}

          {d.latitude !== null && d.longitude !== null && (
            <section>
              <h2>{t("public.sections.location")}</h2>
              <Suspense fallback={<div className="public-map map-loading" />}>
                <PublicMap items={[]} single={{ latitude: d.latitude, longitude: d.longitude, approximate: d.approximateLocation }} formatPrice={() => ""} />
              </Suspense>
              {d.approximateLocation && <p className="hint">{t("public.approximateHint")}</p>}
            </section>
          )}

          <p className="hint public-meta">
            {t("public.listingId", { id: d.id.slice(0, 8).toUpperCase() })}
            {d.publishedAt && <> · {t("public.published", { date: new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium" }).format(new Date(d.publishedAt)) })}</>}
          </p>
        </div>

        <aside className="public-side">
          <div className="panel contact-card">
            {lead && (
              <div className="agent-block">
                <span className="agent-avatar" aria-hidden="true">
                  {lead.firstName[0]}
                  {lead.lastName[0]}
                </span>
                <div>
                  <strong>
                    {lead.firstName} {lead.lastName}
                  </strong>
                  <div className="hint">{d.agency.name}</div>
                  {lead.phone && (
                    <a className="agent-phone" href={`tel:${lead.phone.replace(/\s/g, "")}`}>
                      {lead.phone}
                    </a>
                  )}
                </div>
              </div>
            )}
            <InquiryForm slug={slug!} propertyId={d.id} />
          </div>
        </aside>
      </div>

      {d.similar.length > 0 && (
        <section className="similar">
          <h2>{t("public.sections.similar")}</h2>
          <ul className="property-grid public-grid">
            {d.similar.map((c) => (
              <li key={c.id}>
                <Link to={`/p/${slug}/${c.id}`} className="property-card">
                  <span className="property-cover">{c.coverUrl ? <img src={c.coverUrl} alt="" loading="lazy" /> : <span className="property-cover-empty">{t("properties.noPhoto")}</span>}</span>
                  <span className="property-body">
                    <span className="property-price">{money(c.price, c.currency, i18n.language)}</span>
                    <span className="property-title">{c.title || t(`propertyTypes.${c.type}`)}</span>
                    <span className="property-facts">{facts(c, t).join(" · ")}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {lead?.phone && (
        <div className="public-mobile-bar">
          <span className="price-main">{money(d.price, d.currency, i18n.language)}</span>
          <a className="button-link" href={`tel:${lead.phone.replace(/\s/g, "")}`}>
            {t("public.call")}
          </a>
        </div>
      )}

      {lightbox !== null && (
        <Lightbox
          items={photos.map((p, i) => ({ id: String(i), kind: "PHOTO", position: i, cover: i === 0, caption: p.caption, thumbUrl: p.thumbUrl, url: p.url, externalUrl: null, mimeType: null, sizeBytes: null, width: null, height: null, originalFilename: null }))}
          startIndex={lightbox}
          onClose={() => setLightbox(null)}
        />
      )}
    </article>
  );
}

function InquiryForm({ slug, propertyId }: { slug: string; propertyId: string }) {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [message, setMessage] = useState("");
  const [state, setState] = useState<"idle" | "sending" | "sent" | "error">("idle");
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setState("sending");
    setError(null);
    try {
      await api.post(`/api/public/agencies/${slug}/properties/${propertyId}/inquiries`, { name, phone, email: null, message });
      setState("sent");
    } catch (err) {
      setState("error");
      setError(err instanceof ApiError && err.errors[0] ? t(`clients.errors.${err.errors[0].code}`, { defaultValue: t("public.inquiry.error") }) : t("public.inquiry.error"));
    }
  }

  if (state === "sent")
    return (
      <p className="inquiry-sent" role="status">
        ✓ {t("public.inquiry.sent")}
      </p>
    );

  return (
    <form className="form inquiry-form" onSubmit={submit}>
      <h3>{t("public.inquiry.title")}</h3>
      <input placeholder={t("public.inquiry.name")} value={name} required onChange={(e) => setName(e.target.value)} />
      <input type="tel" placeholder={t("public.inquiry.phone")} value={phone} required onChange={(e) => setPhone(e.target.value)} />
      <textarea rows={3} placeholder={t("public.inquiry.message")} value={message} onChange={(e) => setMessage(e.target.value)} />
      {error && <p className="error">{error}</p>}
      <button type="submit" className="button-block" disabled={state === "sending"}>
        {state === "sending" ? t("public.inquiry.sending") : t("public.inquiry.submit")}
      </button>
      <p className="hint">{t("public.inquiry.hint")}</p>
    </form>
  );
}
