package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.listing.CreateListingRequest;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingRepository;
import com.memphisreo.listing.ListingService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;
    private final ListingRepository listingRepository;

    public ListingController(ListingService listingService, ListingRepository listingRepository) {
        this.listingService = listingService;
        this.listingRepository = listingRepository;
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
        Listing listing = listingService.create(principal.tenantId(), principal.agentId(), request);
        return ResponseEntity.ok(listing);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_VIEW.name())")
    public ResponseEntity<Listing> get(@PathVariable UUID id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Listing не знайдено: " + id));
        return ResponseEntity.ok(listing);
    }
}
