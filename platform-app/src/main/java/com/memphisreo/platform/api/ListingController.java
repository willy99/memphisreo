package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.listing.CreateListingRequest;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingRepository;
import com.memphisreo.listing.ListingService;
import com.memphisreo.property.Address;
import com.memphisreo.property.AddressRepository;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.search.ListingSearchDocument;
import com.memphisreo.search.SearchIndexer;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Композиційний корінь для Listing: після listingService.create() явно
 * збирає {@link ListingSearchDocument} з Property+Address (інший модуль) і
 * синхронізує через {@link SearchIndexer} — той самий патерн, що й
 * TenantRegistrationService/AgentInvitationService. ListingService сам
 * не знає про property/search модулі (межа модуля — тільки сирі UUID).
 * docs/architecture.md §8.
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;
    private final ListingRepository listingRepository;
    private final PropertyRepository propertyRepository;
    private final AddressRepository addressRepository;
    private final SearchIndexer searchIndexer;

    public ListingController(ListingService listingService,
                              ListingRepository listingRepository,
                              PropertyRepository propertyRepository,
                              AddressRepository addressRepository,
                              SearchIndexer searchIndexer) {
        this.listingService = listingService;
        this.listingRepository = listingRepository;
        this.propertyRepository = propertyRepository;
        this.addressRepository = addressRepository;
        this.searchIndexer = searchIndexer;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_VIEW.name())")
    public ResponseEntity<List<Listing>> list() {
        return ResponseEntity.ok(listingRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_PUBLISH.name())")
    public ResponseEntity<Listing> create(@AuthenticationPrincipal AuthenticatedAgent principal,
                                           @RequestBody CreateListingRequest request) {
        // Об'єкт чужої агенції тут не знайдеться (@TenantId + RLS) → 404, а не
        // помилка складеного FK з глибини БД (500).
        propertyRepository.findById(request.propertyId())
                .orElseThrow(() -> new NotFoundException("Обʼєкт нерухомості не знайдено: " + request.propertyId()));
        Listing listing = listingService.create(principal.tenantId(), principal.agentId(), request);
        syncSearchIndex(listing);
        return ResponseEntity.ok(listing);
    }

    private void syncSearchIndex(Listing listing) {
        Property property = propertyRepository.findById(listing.getPropertyId())
                .orElseThrow(() -> new NotFoundException("Обʼєкт нерухомості не знайдено: " + listing.getPropertyId()));
        Address address = addressRepository.findById(property.getAddressId())
                .orElseThrow(() -> new NotFoundException("Адресу не знайдено: " + property.getAddressId()));

        ListingSearchDocument document = new ListingSearchDocument();
        document.setListingId(listing.getId());
        document.setTenantId(listing.getTenantId());
        document.setPropertyId(property.getId());
        document.setDealType(listing.getDealType().name());
        document.setPropertyType(property.getType().name());
        document.setStatus(listing.getStatus().name());
        document.setCountryCode(address.getCountryCode());
        document.setRegion(address.getRegion());
        document.setCity(address.getCity());
        document.setDistrict(address.getDistrict());
        document.setGeoLocation(address.getGeoLocation());
        document.setPrice(listing.getPrice());
        document.setCurrency(listing.getCurrency());
        document.setAreaSqm(property.getAreaSqm());
        document.setLandAreaSqm(property.getLandAreaSqm());
        document.setRooms(property.getRooms());
        document.setBedrooms(property.getBedrooms());
        document.setBathrooms(property.getBathrooms());
        document.setFloor(property.getFloor());
        document.setTotalFloors(property.getTotalFloors());
        document.setYearBuilt(property.getYearBuilt());
        document.setDescription(property.getDescription());
        document.setPublishedAt(listing.getPublishedAt());

        searchIndexer.index(document);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_VIEW.name())")
    public ResponseEntity<Listing> get(@PathVariable UUID id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Listing не знайдено: " + id));
        return ResponseEntity.ok(listing);
    }
}
