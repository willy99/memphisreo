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
  rooms: number | null;
  floor: number | null;
  totalFloors: number | null;
  yearBuilt: number | null;
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
  rooms?: number;
  floor?: number;
  totalFloors?: number;
  yearBuilt?: number;
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
