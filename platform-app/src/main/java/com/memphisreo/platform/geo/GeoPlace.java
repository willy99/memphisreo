package com.memphisreo.platform.geo;

/** Нормалізований результат геокодування — однаковий для будь-якого провайдера. */
public record GeoPlace(
        String label,
        String street,
        String houseNumber,
        String city,
        String district,
        String region,
        String postcode,
        String countryCode,
        double latitude,
        double longitude
) {
}
