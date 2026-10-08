package com.memphisreo.platform.publicsite;

import com.memphisreo.property.Property;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Публічна сторінка агенції: лише те, що можна показувати будь-кому. */
public final class PublicDtos {

    private PublicDtos() {
    }

    public record AgencyInfo(String slug, String name, String phone, String email, String website, String about,
                             String city, long activeListings) {
    }

    public record PublicAgent(String firstName, String lastName, String phone, String email) {
    }

    public record PublicCard(UUID id, Property.Type type, String title, String city, String district,
                             String street, String houseNumber, String complexName, BigDecimal price, String currency,
                             BigDecimal areaSqm, BigDecimal landAreaSqm, Integer rooms, Integer floor, Integer totalFloors,
                             String coverUrl, int photoCount, Double latitude, Double longitude, boolean approximateLocation,
                             Instant publishedAt, boolean priceReduced) {
    }

    public record PublicPhoto(String url, String thumbUrl, String caption) {
    }

    public record PublicDetails(UUID id, Property.Type type, String title, String description,
                                String city, String district, String street, String houseNumber, String complexName,
                                Double latitude, Double longitude, boolean approximateLocation,
                                BigDecimal price, String currency, BigDecimal areaSqm, BigDecimal livingAreaSqm,
                                BigDecimal kitchenAreaSqm, BigDecimal landAreaSqm, Integer rooms, Integer bedrooms,
                                Integer bathrooms, Integer floor, Integer totalFloors, Integer yearBuilt,
                                BigDecimal ceilingHeightM, Property.Market market, Property.WallMaterial wallMaterial,
                                Property.Condition condition, Property.Heating heating, Property.LandPurpose landPurpose,
                                Property.CommercialType commercialType, Boolean hasElevator, Integer parkingSpaces,
                                List<Property.Feature> features, List<PublicPhoto> photos, List<PublicPhoto> floorplans,
                                List<String> videoUrls, List<PublicAgent> agents, AgencyInfo agency, Instant publishedAt,
                                List<PublicCard> similar) {
    }

    public record SearchResult(List<PublicCard> items, long total, int page, int size,
                               List<String> districts, BigDecimal minPrice, BigDecimal maxPrice) {
    }

    public record InquiryRequest(String name, String phone, String email, String message) {
    }
}
