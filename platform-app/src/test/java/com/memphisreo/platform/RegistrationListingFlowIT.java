package com.memphisreo.platform;

import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;

import com.memphisreo.platform.sale.SaleDtos.SaleView;
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

        PropertyDetails property = createProperty(token);
        assertThat(property.id()).isNotNull();
        assertThat(property.status()).isEqualTo(Property.Status.DRAFT);

        SaleView sale = listProperty(token, property.id());
        assertThat(sale.listingId()).isNotNull();
        assertThat(sale.status()).isEqualTo(Listing.Status.ACTIVE);

        ResponseEntity<SaleView> fetched = restTemplate.exchange(
                "/api/properties/" + property.id() + "/sale", HttpMethod.GET, authed(token), SaleView.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().listingId()).isEqualTo(sale.listingId());
    }

    @Test
    void secondTenant_cannotSeeFirstTenantsData() {
        String slugA = "iso-a-" + UUID.randomUUID().toString().substring(0, 8);
        String emailA = "admin@" + slugA + ".ua";
        register(slugA, emailA, "PasswordA123!", "UA");
        String tokenA = login(emailA, "PasswordA123!");
        PropertyDetails propertyA = createProperty(tokenA);
        listProperty(tokenA, propertyA.id());

        String slugB = "iso-b-" + UUID.randomUUID().toString().substring(0, 8);
        String emailB = "admin@" + slugB + ".de";
        register(slugB, emailB, "PasswordB123!", "DE");
        String tokenB = login(emailB, "PasswordB123!");

        ResponseEntity<String> crossTenantRead = restTemplate.exchange(
                "/api/properties/" + propertyA.id() + "/sale", HttpMethod.GET, authed(tokenB), String.class);
        assertThat(crossTenantRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticatedRequest_isRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/properties/" + UUID.randomUUID() + "/sale", HttpMethod.GET, HttpEntity.EMPTY, String.class);
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
