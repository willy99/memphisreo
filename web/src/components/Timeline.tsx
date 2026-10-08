import { useTranslation } from "react-i18next";
import type { TimelineEntry } from "../api/types";

/** Стрічка подій. Текст — з type + payload через i18n: бекенд тексту не формує. */
export function Timeline({ entries, emptyText }: { entries: TimelineEntry[]; emptyText: string }) {
  const { t, i18n } = useTranslation();
  const format = new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "short" });
  const money = (value: unknown, currency: unknown) =>
    typeof value === "string" || typeof value === "number"
      ? new Intl.NumberFormat(i18n.language, { style: "currency", currency: String(currency ?? "USD"), maximumFractionDigits: 0 }).format(Number(value))
      : "";

  if (entries.length === 0) return <p className="hint">{emptyText}</p>;

  return (
    <ol className="timeline">
      {entries.map((e) => {
        const p = e.payload ?? {};
        const text = t(`timeline.${e.type}`, {
          defaultValue: e.type,
          price: money(p.price, p.currency),
          previousPrice: money(p.previousPrice, p.previousCurrency ?? p.currency),
          finalPrice: money(p.finalPrice, p.currency),
          name: String(p.name ?? ""),
          reason: String(p.reason ?? ""),
          kind: t(`propertyForm.media.tabs.${String(p.kind ?? "PHOTO").replace("VIDEO_LINK", "VIDEO")}`, { defaultValue: "" }),
          agents: Array.isArray(p.agents) ? (p.agents as string[]).join(", ") : "",
          validUntil: String(p.validUntil ?? ""),
        });
        return (
          <li key={e.id} className={`timeline-item${e.ownerVisible ? "" : " timeline-internal"}`}>
            <span className="timeline-dot" aria-hidden="true" />
            <div>
              <div className="timeline-text">{text}</div>
              <div className="timeline-meta">
                {format.format(new Date(e.createdAt))}
                {e.actor && <> · {e.actor.firstName} {e.actor.lastName}</>}
                {!e.ownerVisible && <> · {t("timeline.internal")}</>}
              </div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
