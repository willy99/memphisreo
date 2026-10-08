import type { PropertyType } from "../../api/types";

/**
 * Конфігурація форми об'єкта: які поля видно для типу, переліки значень,
 * групи зручностей. Обов'язковість — з бекенда (GET /api/properties/form-schema),
 * щоб правила не розходилися. Переліки — ті самі коди, що в Java enum Property.*.
 */

export const PROPERTY_TYPES: PropertyType[] = ["APARTMENT", "HOUSE", "LAND", "COMMERCIAL", "OTHER"];
export const MARKETS = ["SECONDARY", "NEW_BUILD"] as const;
export const WALL_MATERIALS = [
  "BRICK", "PANEL", "MONOLITH", "MONOLITH_BRICK", "MONOLITH_FRAME", "AERATED_CONCRETE", "FOAM_BLOCK",
  "SHELL_ROCK", "CERAMIC_BLOCK", "WOOD", "FRAME", "SIP", "OTHER",
] as const;
export const CONDITIONS = ["DESIGNER", "EURO", "GOOD", "COSMETIC", "NEEDS_RENOVATION", "WHITE_BOX", "SHELL"] as const;
export const HEATING = ["CENTRAL", "INDIVIDUAL_GAS", "INDIVIDUAL_ELECTRIC", "AUTONOMOUS", "SOLID_FUEL", "HEAT_PUMP", "NONE"] as const;
export const LAND_PURPOSES = [
  "RESIDENTIAL", "GARDENING", "PERSONAL_FARMING", "AGRICULTURAL", "COMMERCIAL", "INDUSTRIAL", "RECREATIONAL", "OTHER",
] as const;
export const COMMERCIAL_TYPES = ["OFFICE", "RETAIL", "WAREHOUSE", "INDUSTRIAL", "HOSPITALITY", "FREE_PURPOSE", "BUILDING", "OTHER"] as const;
export const CURRENCIES = ["USD", "UAH", "EUR"] as const;

export const FEATURE_GROUPS = {
  comfort: ["BALCONY", "LOGGIA", "TERRACE", "FURNISHED", "APPLIANCES", "AIR_CONDITIONING", "STORAGE_ROOM"],
  building: ["UNDERGROUND_PARKING", "GARAGE", "SECURITY", "CONCIERGE", "VIDEO_SURVEILLANCE", "CLOSED_AREA", "PLAYGROUND"],
  resilience: ["BACKUP_POWER", "SHELTER"],
  plot: ["POOL", "SAUNA", "GARDEN", "FENCE", "SUMMER_KITCHEN", "GARAGE"],
  utilities: ["GAS", "ELECTRICITY", "WATER_CENTRAL", "WATER_WELL", "SEWAGE_CENTRAL", "SEPTIC"],
  commercial: ["SEPARATE_ENTRANCE", "SHOP_WINDOW", "AIR_CONDITIONING", "SECURITY", "UNDERGROUND_PARKING"],
} as const;

export type FeatureGroup = keyof typeof FEATURE_GROUPS;

export const FEATURE_GROUPS_BY_TYPE: Record<PropertyType, FeatureGroup[]> = {
  APARTMENT: ["comfort", "building", "resilience"],
  HOUSE: ["comfort", "plot", "utilities", "resilience"],
  LAND: ["utilities"],
  COMMERCIAL: ["commercial", "resilience"],
  OTHER: ["comfort", "utilities"],
};

/** Поля форми (ключі = ключі помилок бекенда), видимі для типу. */
export const VISIBLE_FIELDS: Record<PropertyType, string[]> = {
  APARTMENT: [
    "market", "address.complexName", "unitNumber", "areaSqm", "livingAreaSqm", "kitchenAreaSqm", "rooms", "bathrooms",
    "floor", "totalFloors", "yearBuilt", "ceilingHeightM", "wallMaterial", "condition", "heating", "hasElevator",
    "parkingSpaces",
  ],
  HOUSE: [
    "market", "address.complexName", "areaSqm", "livingAreaSqm", "kitchenAreaSqm", "landAreaSqm", "rooms", "bedrooms",
    "bathrooms", "totalFloors", "yearBuilt", "ceilingHeightM", "wallMaterial", "condition", "heating",
    "cadastralNumber", "parkingSpaces",
  ],
  LAND: ["landAreaSqm", "landPurpose", "cadastralNumber"],
  COMMERCIAL: [
    "commercialType", "unitNumber", "areaSqm", "rooms", "floor", "totalFloors", "yearBuilt", "ceilingHeightM",
    "condition", "heating", "hasElevator", "parkingSpaces",
  ],
  OTHER: ["areaSqm", "yearBuilt", "condition"],
};

/** Секції форми; поле → секція (для статусу секції в навігації). */
export const SECTIONS = ["basics", "location", "parameters", "features", "price", "description", "media"] as const;
export type SectionId = (typeof SECTIONS)[number];

export function sectionOf(field: string): SectionId {
  if (field === "type" || field === "market" || field === "commercialType" || field === "landPurpose") return "basics";
  if (field.startsWith("address.") || field === "location" || field === "unitNumber") return "location";
  if (field.startsWith("price.")) return "price";
  if (field === "title" || field === "description") return "description";
  if (field === "photos" || field === "file") return "media";
  if (field === "features") return "features";
  return "parameters";
}

/** Ділянка в Україні — у сотках (1 сотка = 100 м²); у БД завжди м². */
export const SQM_PER_SOTKA = 100;
