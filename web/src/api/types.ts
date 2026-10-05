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
  schemaName: string;
  adminAgentId: string;
}

export type PropertyType = "APARTMENT" | "HOUSE" | "LAND" | "COMMERCIAL" | "OTHER";
export type PropertyStatus = "DRAFT" | "ACTIVE" | "RESERVED" | "SOLD" | "RENTED" | "ARCHIVED";

export interface Property {
  id: string;
  type: PropertyType;
  addressId: string;
  unitNumber: string | null;
  areaSqm: number;
  landAreaSqm: number | null;
  rooms: number | null;
  bedrooms: number | null;
  bathrooms: number | null;
  floor: number | null;
  totalFloors: number | null;
  yearBuilt: number | null;
  hasElevator: boolean | null;
  parkingSpaces: number | null;
  description: string | null;
  attributesJson: string | null;
  status: PropertyStatus;
  createdByAgentId: string;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePropertyRequest {
  type: PropertyType;
  unitNumber?: string;
  areaSqm: number;
  landAreaSqm?: number;
  rooms?: number;
  bedrooms?: number;
  bathrooms?: number;
  floor?: number;
  totalFloors?: number;
  yearBuilt?: number;
  hasElevator?: boolean;
  parkingSpaces?: number;
  description?: string;
  attributesJson?: string;
  countryCode: string;
  region?: string;
  city: string;
  district?: string;
  street: string;
  houseNumber: string;
  postalCode?: string;
  latitude?: number;
  longitude?: number;
}

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
