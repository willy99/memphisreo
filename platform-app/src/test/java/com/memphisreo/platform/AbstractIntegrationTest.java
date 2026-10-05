package com.memphisreo.platform;

import com.memphisreo.listing.CreateListingRequest;
import com.memphisreo.listing.Listing;
import com.memphisreo.platform.api.AuthController;
import com.memphisreo.platform.registration.RegisterTenantRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.property.CreatePropertyRequest;
import com.memphisreo.property.Property;
import com.memphisreo.security.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Один живий Postgres+PostGIS-контейнер на ВЕСЬ тестовий прогін, спільний
 * для всіх *IT класів — не через @Container (той зупиняє контейнер після
 * кожного класу навіть при спільному static-полі: наступний клас отримує
 * мертве з'єднання). Singleton-container pattern: ручний старт у static-
 * блоці, без .stop() — прибирається Ryuk-ом наприкінці JVM. @ServiceConnection
 * не потребує @Container для роботи, тільки готовий/запущений контейнер.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * JDK-default {@code HttpURLConnection}-based клієнт не вміє PATCH —
     * давня обмеженість самого java.net, не бекенду (PATCH — коректний
     * метод для часткового оновлення, лишається як є). JdkClientHttpRequestFactory
     * (на базі java.net.http.HttpClient, Spring 6.1+) підтримує всі методи.
     */
    @TestConfiguration
    static class PatchCapableRestTemplateConfig {
        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder().requestFactory(() -> new JdkClientHttpRequestFactory());
        }
    }

    protected RegisterTenantResponse register(String slug, String email, String password, String countryCode) {
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Agency " + slug, slug, countryCode, email, password, "Test", "Admin");
        ResponseEntity<RegisterTenantResponse> response =
                restTemplate.postForEntity("/api/public/tenants/register", request, RegisterTenantResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    protected String login(String email, String password) {
        AuthController.LoginRequest request = new AuthController.LoginRequest(email, password);
        ResponseEntity<LoginService.LoginResult> response =
                restTemplate.postForEntity("/api/auth/login", request, LoginService.LoginResult.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
    }

    protected Property createProperty(String token, String countryCode, String attributesJson) {
        CreatePropertyRequest request = new CreatePropertyRequest(
                Property.Type.APARTMENT, "12", new BigDecimal("54.5"), null, 2, 1, 1, 3, 9, 2015,
                true, 1, "Test property", attributesJson, countryCode, null, "Kyiv", "Pecherskyi",
                "Khreshchatyk", "1", "01001", 50.4501, 30.5234);
        ResponseEntity<Property> response = restTemplate.exchange(
                "/api/properties", HttpMethod.POST, authed(token, request), Property.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    protected Listing createListing(String token, UUID propertyId) {
        CreateListingRequest request = new CreateListingRequest(
                propertyId, Listing.DealType.SALE, new BigDecimal("95000"), "USD");
        ResponseEntity<Listing> response = restTemplate.exchange(
                "/api/listings", HttpMethod.POST, authed(token, request), Listing.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    protected HttpEntity<Void> authed(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    protected <T> HttpEntity<T> authed(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
