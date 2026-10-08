package com.memphisreo.platform.property;

import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingService;
import com.memphisreo.media.MediaService;
import com.memphisreo.platform.property.PropertyEditorDtos.FormSchema;
import com.memphisreo.platform.property.PropertyEditorDtos.Price;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyCard;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyPayload;
import com.memphisreo.property.Address;
import com.memphisreo.property.AddressRepository;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyAgent;
import com.memphisreo.property.PropertyAgentRepository;
import com.memphisreo.property.PropertyForm;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.property.PropertyRules;
import com.memphisreo.property.PropertyService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Композиційний корінь редактора об'єкта: модуль property не знає про ціну
 * (listing) і медіа (media) — їх поєднує цей сервіс (docs/architecture.md §2).
 */
@Service
public class PropertyEditorService {

    static final Set<String> CURRENCIES = Set.of("USD", "UAH", "EUR");
    static final int RECOMMENDED_PHOTOS = 5;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000000000");

    private final PropertyService propertyService;
    private final PropertyRepository propertyRepository;
    private final AddressRepository addressRepository;
    private final ListingService listingService;
    private final MediaService mediaService;
    private final PropertyAgentRepository propertyAgentRepository;
    private final ActivityRecorder activity;

    public PropertyEditorService(PropertyService propertyService, PropertyRepository propertyRepository,
                                 AddressRepository addressRepository, ListingService listingService,
                                 MediaService mediaService, PropertyAgentRepository propertyAgentRepository,
                                 ActivityRecorder activity) {
        this.propertyService = propertyService;
        this.propertyRepository = propertyRepository;
        this.addressRepository = addressRepository;
        this.listingService = listingService;
        this.mediaService = mediaService;
        this.propertyAgentRepository = propertyAgentRepository;
        this.activity = activity;
    }

    @Transactional
    public PropertyDetails create(UUID tenantId, UUID agentId, PropertyPayload payload) {
        validatePrice(payload.price());
        Property property = propertyService.createDraft(tenantId, agentId, payload.property());
        // Автор — головний відповідальний агент; чернетка продажу — одразу (ціна може бути порожня).
        PropertyAgent lead = new PropertyAgent();
        lead.setTenantId(tenantId);
        lead.setPropertyId(property.getId());
        lead.setAgentId(agentId);
        lead.setRole(PropertyAgent.Role.LEAD);
        propertyAgentRepository.save(lead);
        listingService.ensureDraft(tenantId, property.getId(), agentId);
        activity.record(tenantId, SubjectType.PROPERTY, property.getId(), "PROPERTY_CREATED", Map.of(), agentId, true);
        savePrice(tenantId, agentId, property.getId(), payload.price());
        return details(property.getId());
    }

    @Transactional
    public PropertyDetails update(UUID tenantId, UUID agentId, UUID propertyId, PropertyPayload payload) {
        validatePrice(payload.price());
        Property property = propertyService.update(propertyId, payload.property());
        if (property.getStatus() != Property.Status.DRAFT && (payload.price() == null || payload.price().amount() == null)) {
            throw new ValidationException("Не заповнено обов'язкові поля", List.of(new FieldError("price.amount", "required")));
        }
        savePrice(tenantId, agentId, propertyId, payload.price());
        return details(propertyId);
    }

    @Transactional
    public PropertyDetails complete(UUID tenantId, UUID agentId, UUID propertyId) {
        List<FieldError> extra = new ArrayList<>();
        if (listingService.current(propertyId).map(Listing::getPrice).isEmpty()) {
            extra.add(new FieldError("price.amount", "required"));
        }
        boolean wasDraft = propertyService.get(propertyId).getStatus() == Property.Status.DRAFT;
        propertyService.complete(propertyId, extra);
        if (wasDraft) {
            activity.record(tenantId, SubjectType.PROPERTY, propertyId, "PROPERTY_COMPLETED", Map.of(), agentId, true);
        }
        return details(propertyId);
    }

    public PropertyDetails details(UUID propertyId) {
        Property p = propertyService.get(propertyId);
        Address a = propertyService.getAddress(p);
        Optional<Listing> listing = listingService.current(propertyId);
        List<FieldError> missing = new ArrayList<>(PropertyRules.missingRequired(p, a));
        if (listing.map(Listing::getPrice).isEmpty()) {
            missing.add(new FieldError("price.amount", "required"));
        }
        return new PropertyDetails(p.getId(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt(), toForm(p, a),
                listing.map(l -> new Price(l.getPrice(), l.getCurrency())).orElse(null),
                mediaService.list(propertyId), missing);
    }

    public List<PropertyCard> cards() {
        List<Property> properties = propertyRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<UUID> ids = properties.stream().map(Property::getId).toList();
        Map<UUID, Address> addresses = addressRepository
                .findAllById(properties.stream().map(Property::getAddressId).distinct().toList())
                .stream().collect(Collectors.toMap(Address::getId, Function.identity()));
        Map<UUID, Listing> listings = listingService.currentByProperty(ids);
        Map<UUID, String> covers = mediaService.coverThumbs(ids);
        Map<UUID, Long> photoCounts = mediaService.photoCounts(ids);
        return properties.stream().map(p -> {
            Address a = addresses.get(p.getAddressId());
            Listing l = listings.get(p.getId());
            return new PropertyCard(p.getId(), p.getType(), p.getStatus(), p.getTitle(),
                    a != null ? a.getCity() : null, a != null ? a.getDistrict() : null,
                    a != null ? a.getStreet() : null, a != null ? a.getHouseNumber() : null,
                    p.getUnitNumber(), a != null ? a.getComplexName() : null,
                    p.getAreaSqm(), p.getLandAreaSqm(), p.getRooms(), p.getFloor(), p.getTotalFloors(),
                    l != null ? l.getPrice() : null, l != null ? l.getCurrency() : null,
                    covers.get(p.getId()), photoCounts.getOrDefault(p.getId(), 0L), p.getUpdatedAt());
        }).toList();
    }

    public FormSchema schema() {
        return new FormSchema(PropertyRules.required(), PropertyRules.recommended(),
                List.of("USD", "UAH", "EUR"), RECOMMENDED_PHOTOS);
    }

    private void savePrice(UUID tenantId, UUID agentId, UUID propertyId, Price price) {
        if (price != null && price.amount() != null) {
            listingService.setAskingPrice(tenantId, propertyId, agentId, price.amount(), price.currency())
                    .ifPresent(change -> activity.record(tenantId, SubjectType.PROPERTY, propertyId, change.event(),
                            new HashMap<>(change.details()), agentId, true));
        }
    }

    private static void validatePrice(Price price) {
        if (price == null || price.amount() == null) {
            return;
        }
        List<FieldError> errors = new ArrayList<>();
        if (price.amount().signum() <= 0 || price.amount().compareTo(MAX_PRICE) > 0) {
            errors.add(new FieldError("price.amount", "outOfRange"));
        }
        if (price.currency() == null || !CURRENCIES.contains(price.currency())) {
            errors.add(new FieldError("price.currency", "invalid"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Некоректна ціна", errors);
        }
    }

    private static PropertyForm toForm(Property p, Address a) {
        Double lat = a.getGeoLocation() != null ? a.getGeoLocation().getY() : null;
        Double lon = a.getGeoLocation() != null ? a.getGeoLocation().getX() : null;
        return new PropertyForm(p.getType(), p.getMarket(), p.getTitle(), p.getDescription(),
                p.getAreaSqm(), p.getLivingAreaSqm(), p.getKitchenAreaSqm(), p.getLandAreaSqm(),
                p.getRooms(), p.getBedrooms(), p.getBathrooms(), p.getFloor(), p.getTotalFloors(),
                p.getYearBuilt(), p.getCeilingHeightM(), p.getWallMaterial(), p.getCondition(), p.getHeating(),
                p.getLandPurpose(), p.getCommercialType(), p.getCadastralNumber(), p.getHasElevator(),
                p.getParkingSpaces(), p.getFeatures(), p.getUnitNumber(),
                new PropertyForm.AddressForm(a.getCountryCode(), a.getRegion(), a.getCity(), a.getDistrict(),
                        a.getStreet(), a.getHouseNumber(), a.getPostalCode(), a.getComplexName(), lat, lon,
                        a.getGeocodeSource()));
    }
}
