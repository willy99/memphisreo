import type { PropertyRef } from "../../api/types";

export function propertyLabel(p: PropertyRef | null, fallback: string): string {
  return p?.title || fallback;
}

export function propertyAddress(p: PropertyRef | null): string {
  if (!p) return "";
  return [[p.street, p.houseNumber].filter(Boolean).join(", "), p.district, p.city].filter(Boolean).join(" · ");
}

export function timeRange(iso: string, minutes: number, lang: string): string {
  const start = new Date(iso);
  const end = new Date(start.getTime() + minutes * 60000);
  const f = new Intl.DateTimeFormat(lang, { hour: "2-digit", minute: "2-digit" });
  return `${f.format(start)}–${f.format(end)}`;
}

export function dayLabel(iso: string, lang: string): string {
  return new Intl.DateTimeFormat(lang, { weekday: "short", day: "numeric", month: "long" }).format(new Date(iso));
}

/** Локальний datetime-local → ISO; і назад. */
export function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}

export function nextRoundHour(offsetHours = 24): string {
  const d = new Date(Date.now() + offsetHours * 3600000);
  d.setMinutes(0, 0, 0);
  return toLocalInput(d.toISOString());
}
