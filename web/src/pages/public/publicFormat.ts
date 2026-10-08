import type { PublicCard, PublicDetails } from "../../api/types";
import { SQM_PER_SOTKA } from "../properties/propertyFields";

export function money(value: number | null | undefined, currency: string | null | undefined, lang: string, compact = false): string {
  if (value == null) return "";
  return new Intl.NumberFormat(lang, { style: "currency", currency: currency ?? "USD", maximumFractionDigits: 0, notation: compact ? "compact" : "standard" }).format(value);
}

export function pricePerUnit(c: Pick<PublicCard, "price" | "currency" | "areaSqm" | "landAreaSqm" | "type">, lang: string, sotkaLabel: string): string | null {
  if (c.price == null) return null;
  if (c.type === "LAND" && c.landAreaSqm) return `${money(c.price / (c.landAreaSqm / SQM_PER_SOTKA), c.currency, lang)} / ${sotkaLabel}`;
  if (c.areaSqm) return `${money(c.price / Number(c.areaSqm), c.currency, lang)} / м²`;
  return null;
}

export function addressLine(c: Pick<PublicCard, "street" | "houseNumber" | "district" | "city" | "complexName">): string {
  const street = [c.street, c.houseNumber].filter(Boolean).join(", ");
  return [street, c.complexName ? `ЖК ${c.complexName}` : null, c.district, c.city].filter(Boolean).join(" · ");
}

export function isNew(publishedAt: string | null): boolean {
  return !!publishedAt && Date.now() - new Date(publishedAt).getTime() < 7 * 86400000;
}

export function facts(c: Pick<PublicDetails, "rooms" | "areaSqm" | "landAreaSqm" | "floor" | "totalFloors" | "type">, t: (k: string, o?: Record<string, unknown>) => string): string[] {
  return [
    c.rooms ? t("properties.roomsShort", { count: c.rooms }) : null,
    c.areaSqm ? `${c.areaSqm} м²` : null,
    c.landAreaSqm ? `${+(c.landAreaSqm / SQM_PER_SOTKA).toFixed(2)} ${t("propertyForm.units.sotka")}` : null,
    c.floor != null && c.totalFloors ? t("properties.floorOf", { floor: c.floor, total: c.totalFloors }) : c.totalFloors && c.type === "HOUSE" ? t("public.floors", { count: c.totalFloors }) : null,
  ].filter(Boolean) as string[];
}
