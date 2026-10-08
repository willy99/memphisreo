package com.memphisreo.property;

import com.memphisreo.common.ValidationException.FieldError;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Які поля обов'язкові для завершення об'єкта, залежно від типу, і
 * перевірки коректності значень. Обов'язкові поля — мінімум, який вимагає
 * DIM.RIA для публікації кожного типу (docs/property-form.md §2).
 * Ті самі ключі полів використовує фронт (GET /api/properties/form-schema).
 */
public final class PropertyRules {

    private static final Pattern UA_CADASTRAL = Pattern.compile("^\\d{10}:\\d{2}:\\d{3}:\\d{4}$");

    private static final Map<Property.Type, List<String>> REQUIRED = new EnumMap<>(Map.of(
            Property.Type.APARTMENT, List.of("address.city", "address.street", "address.houseNumber",
                    "market", "areaSqm", "rooms", "floor", "totalFloors"),
            Property.Type.HOUSE, List.of("address.city", "address.street", "areaSqm", "landAreaSqm",
                    "rooms", "totalFloors", "wallMaterial"),
            Property.Type.LAND, List.of("address.city", "landAreaSqm", "landPurpose"),
            Property.Type.COMMERCIAL, List.of("address.city", "address.street", "address.houseNumber",
                    "commercialType", "areaSqm", "floor", "totalFloors"),
            Property.Type.OTHER, List.of("address.city", "areaSqm")));

    /** Не блокують завершення, але впливають на якість оголошення. */
    private static final List<String> RECOMMENDED = List.of("location", "description", "photos");

    private PropertyRules() {
    }

    public static Map<Property.Type, List<String>> required() {
        return REQUIRED;
    }

    public static List<String> recommended() {
        return RECOMMENDED;
    }

    /** Значення, що задані, мають бути коректні — і в чернетці теж. */
    public static List<FieldError> validateValues(PropertyForm f) {
        List<FieldError> errors = new ArrayList<>();
        positive(errors, "areaSqm", f.areaSqm());
        positive(errors, "livingAreaSqm", f.livingAreaSqm());
        positive(errors, "kitchenAreaSqm", f.kitchenAreaSqm());
        positive(errors, "landAreaSqm", f.landAreaSqm());
        range(errors, "rooms", f.rooms(), 1, 100);
        range(errors, "bedrooms", f.bedrooms(), 0, 100);
        range(errors, "bathrooms", f.bathrooms(), 0, 50);
        range(errors, "floor", f.floor(), -5, 200);
        range(errors, "totalFloors", f.totalFloors(), 1, 200);
        range(errors, "parkingSpaces", f.parkingSpaces(), 0, 1000);
        range(errors, "yearBuilt", f.yearBuilt(), 1700, Year.now().getValue() + 6);
        if (f.ceilingHeightM() != null
                && (f.ceilingHeightM().compareTo(new BigDecimal("1.8")) < 0
                || f.ceilingHeightM().compareTo(new BigDecimal("20")) > 0)) {
            errors.add(new FieldError("ceilingHeightM", "outOfRange"));
        }
        if (f.floor() != null && f.totalFloors() != null && f.floor() > f.totalFloors()) {
            errors.add(new FieldError("floor", "floorAboveTotal"));
        }
        if (f.areaSqm() != null && f.livingAreaSqm() != null && f.livingAreaSqm().compareTo(f.areaSqm()) > 0) {
            errors.add(new FieldError("livingAreaSqm", "exceedsTotalArea"));
        }
        if (f.areaSqm() != null && f.kitchenAreaSqm() != null && f.kitchenAreaSqm().compareTo(f.areaSqm()) > 0) {
            errors.add(new FieldError("kitchenAreaSqm", "exceedsTotalArea"));
        }
        if (f.cadastralNumber() != null && !f.cadastralNumber().isBlank()
                && !UA_CADASTRAL.matcher(f.cadastralNumber().trim()).matches()) {
            errors.add(new FieldError("cadastralNumber", "cadastralFormat"));
        }
        if (f.title() != null && f.title().length() > 150) {
            errors.add(new FieldError("title", "tooLong"));
        }
        PropertyForm.AddressForm a = f.address();
        if (a != null && (a.latitude() == null) != (a.longitude() == null)) {
            errors.add(new FieldError("location", "invalid"));
        }
        if (a != null && a.latitude() != null
                && (Math.abs(a.latitude()) > 90 || Math.abs(a.longitude()) > 180)) {
            errors.add(new FieldError("location", "invalid"));
        }
        return errors;
    }

    /** Обов'язкові поля типу, що не заповнені. */
    public static List<FieldError> missingRequired(Property property, Address address) {
        List<FieldError> errors = new ArrayList<>();
        for (String field : REQUIRED.get(property.getType())) {
            if (isBlank(valueOf(field, property, address))) {
                errors.add(new FieldError(field, "required"));
            }
        }
        return errors;
    }

    private static Object valueOf(String field, Property p, Address a) {
        return switch (field) {
            case "address.city" -> a.getCity();
            case "address.street" -> a.getStreet();
            case "address.houseNumber" -> a.getHouseNumber();
            case "market" -> p.getMarket();
            case "areaSqm" -> p.getAreaSqm();
            case "landAreaSqm" -> p.getLandAreaSqm();
            case "rooms" -> p.getRooms();
            case "floor" -> p.getFloor();
            case "totalFloors" -> p.getTotalFloors();
            case "wallMaterial" -> p.getWallMaterial();
            case "landPurpose" -> p.getLandPurpose();
            case "commercialType" -> p.getCommercialType();
            default -> throw new IllegalStateException("Невідоме обов'язкове поле: " + field);
        };
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private static void positive(List<FieldError> errors, String field, BigDecimal value) {
        if (value != null && value.signum() <= 0) {
            errors.add(new FieldError(field, "positive"));
        }
    }

    private static void range(List<FieldError> errors, String field, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            errors.add(new FieldError(field, "outOfRange"));
        }
    }
}
