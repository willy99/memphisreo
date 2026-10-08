package com.memphisreo.platform.api;

import com.memphisreo.inquiry.Inquiry;
import com.memphisreo.platform.publicsite.PublicDtos.AgencyInfo;
import com.memphisreo.platform.publicsite.PublicDtos.InquiryRequest;
import com.memphisreo.platform.publicsite.PublicDtos.PublicDetails;
import com.memphisreo.platform.publicsite.PublicDtos.SearchResult;
import com.memphisreo.platform.publicsite.PublicSiteService;
import com.memphisreo.platform.publicsite.PublicSiteService.Filters;
import com.memphisreo.property.Property;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Публічна сторінка агенції — без автентифікації (/api/public/** permitAll). */
@RestController
@RequestMapping("/api/public/agencies/{slug}")
public class PublicAgencyController {

    private final PublicSiteService publicSiteService;

    public PublicAgencyController(PublicSiteService publicSiteService) {
        this.publicSiteService = publicSiteService;
    }

    @GetMapping
    public ResponseEntity<AgencyInfo> agency(@PathVariable String slug) {
        return ResponseEntity.ok(publicSiteService.info(slug));
    }

    @GetMapping("/properties")
    public ResponseEntity<SearchResult> search(@PathVariable String slug,
                                               @RequestParam(required = false) Property.Type type,
                                               @RequestParam(required = false) Integer roomsMin,
                                               @RequestParam(required = false) BigDecimal priceMin,
                                               @RequestParam(required = false) BigDecimal priceMax,
                                               @RequestParam(required = false) BigDecimal areaMin,
                                               @RequestParam(required = false) BigDecimal areaMax,
                                               @RequestParam(required = false) String district,
                                               @RequestParam(required = false) Property.Market market,
                                               @RequestParam(required = false) List<Property.Feature> features,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(publicSiteService.search(slug, new Filters(type, roomsMin, priceMin, priceMax, areaMin,
                areaMax, district, market, features, q, sort, page, size)));
    }

    @GetMapping("/properties/{propertyId}")
    public ResponseEntity<PublicDetails> details(@PathVariable String slug, @PathVariable UUID propertyId) {
        return ResponseEntity.ok(publicSiteService.details(slug, propertyId));
    }

    @PostMapping("/properties/{propertyId}/inquiries")
    public ResponseEntity<Inquiry> inquire(@PathVariable String slug, @PathVariable UUID propertyId,
                                           @RequestBody InquiryRequest request) {
        return ResponseEntity.ok(publicSiteService.inquire(slug, propertyId, request));
    }
}
