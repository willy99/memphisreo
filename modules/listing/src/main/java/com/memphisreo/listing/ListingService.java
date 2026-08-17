package com.memphisreo.listing;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** property_id — сире посилання, не перевіряється тут (межа модуля з property). */
@Service
public class ListingService {

    private final ListingRepository listingRepository;

    public ListingService(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    public Listing create(UUID tenantId, UUID agentId, CreateListingRequest request) {
        Listing listing = new Listing();
        listing.setPropertyId(request.propertyId());
        listing.setAgentId(agentId);
        listing.setDealType(request.dealType());
        listing.setPrice(request.price());
        listing.setCurrency(request.currency());
        listing.setStatus(Listing.Status.PUBLISHED);
        listing.setTenantId(tenantId);
        listing.setPublishedAt(Instant.now());

        return listingRepository.save(listing);
    }
}
