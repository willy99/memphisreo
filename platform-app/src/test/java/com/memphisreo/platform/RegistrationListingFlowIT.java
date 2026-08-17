package com.memphisreo.platform;

import com.memphisreo.listing.CreateListingRequest;
import com.memphisreo.listing.Listing;
import com.memphisreo.platform.api.AuthController;
import com.memphisreo.platform.registration.RegisterTenantRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.property.CreatePropertyRequest;
import com.memphisreo.property.Property;
import com.memphisreo.security.LoginService;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кодифікує флоу, вручну перевірений curl-ами під час live-верифікації:
 * реєстрація → логін → property → listing → round-trip → ізоляція між
 * tenant-ами. Жоден з 7 багів, знайдених тоді (Flyway placeholder,
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
        assertThat(registration.schemaName()).startsWith("tenant_acme_");

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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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

    private RegisterTenantResponse register(String slug, String email, String password, String countryCode) {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Agency " + slug, slug, countryCode, email, password, "Test", "Admin");
        ResponseEntity<RegisterTenantResponse> response =
                restTemplate.postForEntity("/api/public/tenants/register", request, RegisterTenantResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String login(String email, String password) {
        AuthController.LoginRequest request = new AuthController.LoginRequest(email, password);
        ResponseEntity<LoginService.LoginResult> response =
                restTemplate.postForEntity("/api/auth/login", request, LoginService.LoginResult.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
    }

    private Property createProperty(String token, String countryCode, String attributesJson) {
        CreatePropertyRequest request = new CreatePropertyRequest(
                Property.Type.APARTMENT, "12", new BigDecimal("54.5"), 2, 3, 9, 2015,
                attributesJson, countryCode, null, "Kyiv", "Pecherskyi", "Khreshchatyk", "1", "01001",
                50.4501, 30.5234);
        ResponseEntity<Property> response = restTemplate.exchange(
                "/api/properties", HttpMethod.POST, authed(token, request), Property.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Listing createListing(String token, UUID propertyId) {
        CreateListingRequest request = new CreateListingRequest(
                propertyId, Listing.DealType.SALE, new BigDecimal("95000"), "USD");
        ResponseEntity<Listing> response = restTemplate.exchange(
                "/api/listings", HttpMethod.POST, authed(token, request), Listing.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpEntity<Void> authed(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> authed(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
