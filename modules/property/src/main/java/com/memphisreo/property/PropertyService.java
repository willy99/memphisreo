package com.memphisreo.property;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Об'єкт нерухомості: чернетка → завершений (ACTIVE). Чернетка зберігається
 * з будь-якою повнотою (автозбереження форми), але задані значення мають
 * бути коректні; при завершенні перевіряються обов'язкові поля типу
 * ({@link PropertyRules}). tenant_id/agent_id — з JWT-принципала виклику.
 */
@Service
public class PropertyService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String DEFAULT_COUNTRY = "UA";

    private final AddressRepository addressRepository;
    private final PropertyRepository propertyRepository;

    public PropertyService(AddressRepository addressRepository, PropertyRepository propertyRepository) {
        this.addressRepository = addressRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public Property createDraft(UUID tenantId, UUID agentId, PropertyForm form) {
        requireType(form);
        validateValues(form);

        Address address = new Address();
        address.setTenantId(tenantId);
        applyAddress(address, form.address());
        address = addressRepository.save(address);

        Property property = new Property();
        property.setTenantId(tenantId);
        property.setAddressId(address.getId());
        property.setCreatedByAgentId(agentId);
        property.setStatus(Property.Status.DRAFT);
        applyForm(property, form);
        return propertyRepository.save(property);
    }

    /** Автозбереження форми: повна заміна значень. Завершений об'єкт лишається валідним. */
    @Transactional
    public Property update(UUID propertyId, PropertyForm form) {
        requireType(form);
        validateValues(form);
        Property property = get(propertyId);
        Address address = getAddress(property);
        applyForm(property, form);
        applyAddress(address, form.address());
        if (property.getStatus() != Property.Status.DRAFT) {
            List<FieldError> missing = PropertyRules.missingRequired(property, address);
            if (!missing.isEmpty()) {
                throw new ValidationException("Не заповнено обов'язкові поля", missing);
            }
        }
        property.setUpdatedAt(Instant.now());
        return property;
    }

    /**
     * Чернетка → ACTIVE. {@code extraMissing} — обов'язкові поля інших модулів
     * (напр. ціна з listing), які перевіряє композиційний корінь.
     */
    @Transactional
    public Property complete(UUID propertyId, List<FieldError> extraMissing) {
        Property property = get(propertyId);
        List<FieldError> missing = new ArrayList<>(PropertyRules.missingRequired(property, getAddress(property)));
        missing.addAll(extraMissing);
        if (!missing.isEmpty()) {
            throw new ValidationException("Не заповнено обов'язкові поля", missing);
        }
        if (property.getStatus() == Property.Status.DRAFT) {
            property.setStatus(Property.Status.ACTIVE);
            property.setUpdatedAt(Instant.now());
        }
        return property;
    }

    public Property get(UUID propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Об'єкт не знайдено: " + propertyId));
    }

    public Address getAddress(Property property) {
        return addressRepository.findById(property.getAddressId())
                .orElseThrow(() -> new NotFoundException("Адресу не знайдено: " + property.getAddressId()));
    }

    private static void requireType(PropertyForm form) {
        if (form.type() == null) {
            throw new ValidationException("Не вказано тип об'єкта", List.of(new FieldError("type", "required")));
        }
    }

    private static void validateValues(PropertyForm form) {
        List<FieldError> errors = PropertyRules.validateValues(form);
        if (!errors.isEmpty()) {
            throw new ValidationException("Некоректні значення полів", errors);
        }
    }

    private static void applyForm(Property p, PropertyForm f) {
        p.setType(f.type());
        p.setMarket(f.market());
        p.setTitle(trim(f.title()));
        p.setDescription(trim(f.description()));
        p.setAreaSqm(f.areaSqm());
        p.setLivingAreaSqm(f.livingAreaSqm());
        p.setKitchenAreaSqm(f.kitchenAreaSqm());
        p.setLandAreaSqm(f.landAreaSqm());
        p.setRooms(f.rooms());
        p.setBedrooms(f.bedrooms());
        p.setBathrooms(f.bathrooms());
        p.setFloor(f.floor());
        p.setTotalFloors(f.totalFloors());
        p.setYearBuilt(f.yearBuilt());
        p.setCeilingHeightM(f.ceilingHeightM());
        p.setWallMaterial(f.wallMaterial());
        p.setCondition(f.condition());
        p.setHeating(f.heating());
        p.setLandPurpose(f.landPurpose());
        p.setCommercialType(f.commercialType());
        p.setCadastralNumber(trim(f.cadastralNumber()));
        p.setHasElevator(f.hasElevator());
        p.setParkingSpaces(f.parkingSpaces());
        p.setFeatures(f.features() == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(f.features())));
        p.setUnitNumber(trim(f.unitNumber()));
    }

    private static void applyAddress(Address a, PropertyForm.AddressForm f) {
        PropertyForm.AddressForm form = f != null ? f
                : new PropertyForm.AddressForm(null, null, null, null, null, null, null, null, null, null, null);
        a.setCountryCode(form.countryCode() != null && !form.countryCode().isBlank()
                ? form.countryCode().trim().toUpperCase() : DEFAULT_COUNTRY);
        a.setRegion(trim(form.region()));
        a.setCity(trim(form.city()));
        a.setDistrict(trim(form.district()));
        a.setStreet(trim(form.street()));
        a.setHouseNumber(trim(form.houseNumber()));
        a.setPostalCode(trim(form.postalCode()));
        a.setComplexName(trim(form.complexName()));
        a.setGeocodeSource(form.geocodeSource());
        if (form.latitude() != null && form.longitude() != null) {
            Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(form.longitude(), form.latitude()));
            a.setGeoLocation(point);
        } else {
            a.setGeoLocation(null);
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
