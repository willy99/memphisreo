package com.memphisreo.platform;

import com.memphisreo.listing.Listing;
import com.memphisreo.platform.api.AuthController;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.property.Property;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кодифікує флоу, вручну перевірений curl-ами під час live-верифікації:
 * реєстрація → логін → property → listing → round-trip → ізоляція між
 * tenant-ами. Жоден з багів, знайдених тоді (Flyway placeholder,
 * open-in-view, ddl-auto=validate, Bouncy Castle, executable jar,
 * -parameters, column-мапінг), не був би спійманий без реального Postgres.
 */
class RegistrationListingFlowIT extends AbstractIntegrationTest {

    @Test
    void registerLoginCreatePropertyAndListing_thenReadBack() {
        String slug = "acme-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin+" + slug + "@acme.ua";
        String password = "SuperSecret123!";

        RegisterTenantResponse registration = register(slug, email, password, "UA");
        assertThat(registration.tenantId()).isNotNull();
        assertThat(registration.adminAgentId()).isNotNull();

        String token = login(email, password);
        assertThat(token).isNotBlank();

        Property property = createProperty(token, "UA", "{\"cadastral_number\":\"1234567890:01:002:0003\"}");
        assertThat(property.getId()).isNotNull();
        assertThat(property.getStatus()).isEqualTo(Property.Status.ACTIVE);

        Listing listing = createListing(token, property.getId());
        assertThat(listing.getId()).isNotNull();
        assertThat(listing.getStatus()).isEqualTo(Listing.Status.PUBLISHED);

        ResponseEntity<Listing> fetched = restTemplate.exchange(
                "/api/listings/" + listing.getId(), HttpMethod.GET, authed(token), Listing.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getId()).isEqualTo(listing.getId());
    }

    @Test
    void secondTenant_cannotSeeFirstTenantsData() {
        String slugA = "iso-a-" + UUID.randomUUID().toString().substring(0, 8);
        String emailA = "admin@" + slugA + ".ua";
        register(slugA, emailA, "PasswordA123!", "UA");
        String tokenA = login(emailA, "PasswordA123!");
        Property propertyA = createProperty(tokenA, "UA", "{\"cadastral_number\":\"1234567890:01:002:0003\"}");
        Listing listingA = createListing(tokenA, propertyA.getId());

        String slugB = "iso-b-" + UUID.randomUUID().toString().substring(0, 8);
        String emailB = "admin@" + slugB + ".de";
        register(slugB, emailB, "PasswordB123!", "DE");
        String tokenB = login(emailB, "PasswordB123!");

        ResponseEntity<String> crossTenantRead = restTemplate.exchange(
                "/api/listings/" + listingA.getId(), HttpMethod.GET, authed(tokenB), String.class);
        assertThat(crossTenantRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedRequest_isRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/listings/" + UUID.randomUUID(), HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPassword_isRejected() {
        String slug = "wrongpw-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin@" + slug + ".ua";
        register(slug, email, "CorrectPassword123!", "UA");

        AuthController.LoginRequest badLogin = new AuthController.LoginRequest(email, "WrongPassword!");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login", badLogin, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
