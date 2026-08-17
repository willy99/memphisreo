package com.memphisreo.platform.registration;

public record RegisterTenantRequest(
        String agencyName,
        String slug,
        String countryCode,
        String adminEmail,
        String adminPassword,
        String adminFirstName,
        String adminLastName
) {
}
