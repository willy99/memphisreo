package com.memphisreo.platform.property;

import com.memphisreo.common.ValidationException.FieldError;
import com.memphisreo.media.MediaView;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyForm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO редактора об'єкта: об'єкт (модуль property) + ціна (listing) + медіа (media). */
public final class PropertyEditorDtos {

    private PropertyEditorDtos() {
    }

    public record Price(BigDecimal amount, String currency) {
    }

    /** Тіло автозбереження/створення. */
    public record PropertyPayload(PropertyForm property, Price price) {
    }

    public record PropertyDetails(
            UUID id,
            Property.Status status,
            Instant createdAt,
            Instant updatedAt,
            PropertyForm property,
            Price price,
            List<MediaView> media,
            /** Обов'язкові поля, ще не заповнені (для чек-листа "що лишилось"). */
            List<FieldError> missing
    ) {
    }

    public record PropertyCard(
            UUID id,
            Property.Type type,
            Property.Status status,
            String title,
            String city,
            String district,
            String street,
            String houseNumber,
            String unitNumber,
            String complexName,
            BigDecimal areaSqm,
            BigDecimal landAreaSqm,
            Integer rooms,
            Integer floor,
            Integer totalFloors,
            BigDecimal price,
            String currency,
            String coverThumbUrl,
            long photoCount,
            Instant updatedAt
    ) {
    }

    public record FormSchema(Map<Property.Type, List<String>> required, List<String> recommended,
                             List<String> currencies, int recommendedPhotos) {
    }
}
