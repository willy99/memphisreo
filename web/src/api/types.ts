export interface LoginResult {
  accessToken: string;
  expiresIn: string;
}

export interface RegisterTenantRequest {
  agencyName: string;
  slug: string;
  countryCode: string;
  adminEmail: string;
  adminPassword: string;
  adminFirstName: string;
  adminLastName: string;
}

export interface RegisterTenantResponse {
  tenantId: string;
  adminAgentId: string;
}

export type PropertyType = "APARTMENT" | "HOUSE" | "LAND" | "COMMERCIAL" | "OTHER";
export type PropertyStatus = "DRAFT" | "ACTIVE" | "RESERVED" | "SOLD" | "RENTED" | "ARCHIVED";

export type DealType = "SALE" | "LONG_TERM_RENT" | "SHORT_TERM_RENT";
export type ListingStatus = "DRAFT" | "PUBLISHED" | "RESERVED" | "CLOSED" | "EXPIRED" | "ARCHIVED";

export interface Listing {
  id: string;
  propertyId: string;
  agentId: string;
  dealType: DealType;
  price: number;
  currency: string;
  status: ListingStatus;
  tenantId: string;
  publishedAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateListingRequest {
  propertyId: string;
  dealType: DealType;
  price: number;
  currency: string;
}

export type AgentStatus = "INVITED" | "ACTIVE" | "DISABLED";

export interface Agent {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  licenseNumber: string | null;
  status: AgentStatus;
  tenantId: string;
  createdAt: string;
}

export interface InviteAgentRequest {
  email: string;
  firstName: string;
  lastName: string;
  roleId?: string | null;
}

export interface InviteAgentResponse {
  agentId: string;
  email: string;
  inviteToken: string;
  expiresAt: string;
}

export interface AcceptInviteRequest {
  token: string;
  password: string;
}

export const ALL_PERMISSIONS = [
  "PROPERTY_VIEW", "PROPERTY_CREATE", "PROPERTY_EDIT", "PROPERTY_DELETE",
  "LISTING_VIEW", "LISTING_PUBLISH", "LISTING_EDIT", "LISTING_CLOSE",
  "INQUIRY_VIEW", "INQUIRY_MANAGE",
  "LEAD_VIEW", "LEAD_MANAGE", "CLIENT_VIEW", "CLIENT_MANAGE",
  "AGENT_INVITE", "AGENT_MANAGE", "ROLE_MANAGE",
  "TENANT_SETTINGS_MANAGE",
] as const;
export type PermissionCode = (typeof ALL_PERMISSIONS)[number];

export interface Role {
  id: string;
  name: string;
  isSystemDefault: boolean;
  permissions: PermissionCode[];
}

export interface CreateRoleRequest {
  name: string;
  permissions: PermissionCode[];
}

export interface Tenant {
  id: string;
  name: string;
  slug: string;
  countryCode: string;
  region: string;
  status: string;
  subscriptionPlan: string | null;
}

export interface TenantStats {
  agents: number;
  properties: number;
}

export type TenantStatus = "TRIAL" | "ACTIVE" | "SUSPENDED" | "CHURNED";

export interface TenantSummary {
  id: string;
  name: string;
  slug: string;
  countryCode: string;
  region: string;
  status: TenantStatus;
  createdAt: string;
  agents: number;
  properties: number;
}

export interface TenantPage {
  items: TenantSummary[];
  page: number;
  size: number;
  total: number;
}

export interface PlatformPropertyRow {
  id: string;
  type: PropertyType;
  status: PropertyStatus;
  areaSqm: number;
  rooms: number | null;
  city: string | null;
  district: string | null;
  street: string | null;
  houseNumber: string | null;
  unitNumber: string | null;
  createdAt: string;
}

// ---------- Редактор об'єкта ----------

export interface FieldError {
  field: string;
  code: string;
}

export interface AddressForm {
  countryCode: string | null;
  region: string | null;
  city: string | null;
  district: string | null;
  street: string | null;
  houseNumber: string | null;
  postalCode: string | null;
  complexName: string | null;
  latitude: number | null;
  longitude: number | null;
  geocodeSource: "AUTOCOMPLETE" | "PIN" | "MANUAL" | null;
}

export interface PropertyForm {
  type: PropertyType;
  market: string | null;
  title: string | null;
  description: string | null;
  areaSqm: number | null;
  livingAreaSqm: number | null;
  kitchenAreaSqm: number | null;
  landAreaSqm: number | null;
  rooms: number | null;
  bedrooms: number | null;
  bathrooms: number | null;
  floor: number | null;
  totalFloors: number | null;
  yearBuilt: number | null;
  ceilingHeightM: number | null;
  wallMaterial: string | null;
  condition: string | null;
  heating: string | null;
  landPurpose: string | null;
  commercialType: string | null;
  cadastralNumber: string | null;
  hasElevator: boolean | null;
  parkingSpaces: number | null;
  features: string[];
  unitNumber: string | null;
  address: AddressForm;
}

export interface Price {
  amount: number | null;
  currency: string;
}

export interface PropertyPayload {
  property: PropertyForm;
  price: Price | null;
}

export type MediaKind = "PHOTO" | "FLOORPLAN" | "VIDEO" | "VIDEO_LINK";

export interface MediaView {
  id: string;
  kind: MediaKind;
  position: number;
  cover: boolean;
  caption: string | null;
  thumbUrl: string | null;
  url: string | null;
  externalUrl: string | null;
  mimeType: string | null;
  sizeBytes: number | null;
  width: number | null;
  height: number | null;
  originalFilename: string | null;
}

export interface PropertyDetails {
  id: string;
  status: PropertyStatus;
  createdAt: string;
  updatedAt: string;
  property: PropertyForm;
  price: Price | null;
  media: MediaView[];
  missing: FieldError[];
}

export interface PropertyCard {
  id: string;
  type: PropertyType;
  status: PropertyStatus;
  title: string | null;
  city: string | null;
  district: string | null;
  street: string | null;
  houseNumber: string | null;
  unitNumber: string | null;
  complexName: string | null;
  areaSqm: number | null;
  landAreaSqm: number | null;
  rooms: number | null;
  floor: number | null;
  totalFloors: number | null;
  price: number | null;
  currency: string | null;
  coverThumbUrl: string | null;
  photoCount: number;
  updatedAt: string;
}

export interface FormSchema {
  required: Record<PropertyType, string[]>;
  recommended: string[];
  currencies: string[];
  recommendedPhotos: number;
}

export interface GeoPlace {
  label: string;
  street: string | null;
  houseNumber: string | null;
  city: string | null;
  district: string | null;
  region: string | null;
  postcode: string | null;
  countryCode: string | null;
  latitude: number;
  longitude: number;
}
