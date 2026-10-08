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
export type ListingStatus = "DRAFT" | "ACTIVE" | "UNDER_OFFER" | "SOLD" | "WITHDRAWN" | "EXPIRED";

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
  publicPhone: string | null;
  publicEmail: string | null;
  website: string | null;
  about: string | null;
  publicCity: string | null;
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

export type ClientSource = "WEBSITE_INQUIRY" | "REFERRAL" | "ADVERTISEMENT" | "WALK_IN" | "OTHER";

export interface Client {
  id: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  source: ClientSource;
  notes: string | null;
  createdAt: string;
}

export interface ClientForm {
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  source: ClientSource;
  notes: string | null;
}

// ---------- Продаж, агенти, історія ----------

export type MandateType = "EXCLUSIVE" | "NON_EXCLUSIVE";
export type PropertyAgentRole = "LEAD" | "CO_AGENT";

export interface SellerView {
  id: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  email: string | null;
}

export interface AgentView {
  id: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  email: string | null;
  role: PropertyAgentRole | null;
}

export interface PricePoint {
  price: number;
  currency: string;
  changedAt: string;
}

export interface SaleView {
  listingId: string;
  status: ListingStatus;
  price: number | null;
  currency: string;
  seller: SellerView | null;
  mandateType: MandateType | null;
  mandateValidUntil: string | null;
  commissionPercent: number | null;
  commissionFixed: number | null;
  accessNotes: string | null;
  hideExactAddress: boolean;
  withdrawnReason: string | null;
  publishedAt: string | null;
  closedAt: string | null;
  priceHistory: PricePoint[];
  agents: AgentView[];
  publicUrl: string | null;
  blockers: string[];
}

export interface SaleForm {
  price: number | null;
  currency: string | null;
  sellerClientId: string | null;
  mandateType: MandateType | null;
  mandateValidUntil: string | null;
  commissionPercent: number | null;
  commissionFixed: number | null;
  accessNotes: string | null;
  hideExactAddress: boolean;
}

export interface TimelineEntry {
  id: string;
  type: string;
  payload: Record<string, unknown>;
  actor: AgentView | null;
  ownerVisible: boolean;
  createdAt: string;
}

export interface OwnerLink {
  url: string;
  expiresAt: string;
}

// ---------- Публічна сторінка агенції ----------

export interface AgencyInfo {
  slug: string;
  name: string;
  phone: string | null;
  email: string | null;
  website: string | null;
  about: string | null;
  city: string | null;
  activeListings: number;
}

export interface PublicAgent {
  firstName: string;
  lastName: string;
  phone: string | null;
  email: string | null;
}

export interface PublicCard {
  id: string;
  type: PropertyType;
  title: string | null;
  city: string | null;
  district: string | null;
  street: string | null;
  houseNumber: string | null;
  complexName: string | null;
  price: number | null;
  currency: string | null;
  areaSqm: number | null;
  landAreaSqm: number | null;
  rooms: number | null;
  floor: number | null;
  totalFloors: number | null;
  coverUrl: string | null;
  photoCount: number;
  latitude: number | null;
  longitude: number | null;
  approximateLocation: boolean;
  publishedAt: string | null;
  priceReduced: boolean;
}

export interface PublicPhoto {
  url: string | null;
  thumbUrl: string | null;
  caption: string | null;
}

export interface PublicDetails {
  id: string;
  type: PropertyType;
  title: string | null;
  description: string | null;
  city: string | null;
  district: string | null;
  street: string | null;
  houseNumber: string | null;
  complexName: string | null;
  latitude: number | null;
  longitude: number | null;
  approximateLocation: boolean;
  price: number | null;
  currency: string | null;
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
  market: string | null;
  wallMaterial: string | null;
  condition: string | null;
  heating: string | null;
  landPurpose: string | null;
  commercialType: string | null;
  hasElevator: boolean | null;
  parkingSpaces: number | null;
  features: string[];
  photos: PublicPhoto[];
  floorplans: PublicPhoto[];
  videoUrls: string[];
  agents: PublicAgent[];
  agency: AgencyInfo;
  publishedAt: string | null;
  similar: PublicCard[];
}

export interface SearchResult {
  items: PublicCard[];
  total: number;
  page: number;
  size: number;
  districts: string[];
  minPrice: number | null;
  maxPrice: number | null;
}

// ---------- Кабінет власника ----------

export interface OwnerProperty {
  id: string;
  title: string | null;
  type: PropertyType;
  city: string | null;
  district: string | null;
  street: string | null;
  houseNumber: string | null;
  status: ListingStatus;
  price: number | null;
  currency: string;
  coverUrl: string | null;
  publishedAt: string | null;
  timeline: TimelineEntry[];
}

export interface OwnerReport {
  agencyName: string;
  agencyPhone: string | null;
  ownerFirstName: string;
  properties: OwnerProperty[];
}

// ---------- Адмінка: агенти агенції ----------

export interface AgentAccount {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  status: AgentStatus;
  loginStatus: "PENDING_INVITE" | "ACTIVE" | "DISABLED" | null;
  createdAt: string;
  deletable: boolean;
}

export interface ResetPassword {
  email: string;
  password: string;
}

export interface TenantSummaryLite {
  name: string;
  slug: string;
  publicPhone: string | null;
}

// ---------- Клієнт: запит, підбір, журнал ----------

export interface ClientRequirement {
  id: string;
  clientId: string;
  propertyType: PropertyType | null;
  roomsMin: number | null;
  roomsMax: number | null;
  priceMin: number | null;
  priceMax: number | null;
  currency: string;
  areaMin: number | null;
  districts: string[];
  market: string | null;
  mustHave: string[];
  notes: string | null;
  active: boolean;
  updatedAt: string;
}

export interface RequirementForm {
  propertyType: PropertyType | null;
  roomsMin: number | null;
  roomsMax: number | null;
  priceMin: number | null;
  priceMax: number | null;
  currency: string;
  areaMin: number | null;
  districts: string[];
  market: string | null;
  mustHave: string[];
  notes: string | null;
  active: boolean;
}

export interface PropertyMatch {
  property: PropertyCard;
  score: number;
  matched: string[];
}

export interface ClientMatch {
  clientId: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  score: number;
  matched: string[];
}

export interface OwnedProperty {
  id: string;
  title: string | null;
  status: ListingStatus;
}

export interface ClientDetails {
  client: Client;
  requirement: ClientRequirement | null;
  matches: PropertyMatch[];
  owned: OwnedProperty[];
  timeline: TimelineEntry[];
}
