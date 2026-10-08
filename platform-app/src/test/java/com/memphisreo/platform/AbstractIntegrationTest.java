package com.memphisreo.platform;

import com.memphisreo.listing.CreateListingRequest;
import com.memphisreo.listing.Listing;
import com.memphisreo.platform.api.AuthController;
import com.memphisreo.platform.registration.RegisterTenantRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.platform.property.PropertyEditorDtos.Price;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyPayload;
import com.memphisreo.property.PropertyForm;
import com.memphisreo.property.Property;
import com.memphisreo.security.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.memphisreo.common.mail.EmailSender;
import com.memphisreo.common.storage.ObjectStorage;
import com.memphisreo.platform.geo.GeoPlace;
import com.memphisreo.platform.geo.GeocodingProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Один живий Postgres+PostGIS-контейнер на ВЕСЬ тестовий прогін, спільний
 * для всіх *IT класів — не через @Container (той зупиняє контейнер після
 * кожного класу навіть при спільному static-полі: наступний клас отримує
 * мертве з'єднання). Singleton-container pattern: ручний старт у static-
 * блоці, без .stop() — прибирається Ryuk-ом наприкінці JVM.
 *
 * Без @ServiceConnection: як і в проді, застосунок ходить логіном
 * memphisreo_app_user (не власник, без BYPASSRLS — інакше RLS не діє),
 * а Flyway — суперкористувачем контейнера (ADR-001). Логін застосунку
 * створює той самий init-скрипт, що й локальний docker compose (виконується
 * через JDBC: copyFileToContainer конфліктує з версією commons-lang3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String PLATFORM_ADMIN_EMAIL = "platform-admin@test.local";
    protected static final String PLATFORM_ADMIN_PASSWORD = "PlatformAdmin123!";
    protected static final String APP_DB_USER = "memphisreo_app_user";
    protected static final String APP_DB_PASSWORD = "memphisreo_app";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("../docker/postgres/init/01-app-login.sql")));
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Не вдалося створити логін застосунку в тестовій БД", e);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_DB_USER);
        registry.add("spring.datasource.password", () -> APP_DB_PASSWORD);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("memphisreo.bootstrap-admin.email", () -> PLATFORM_ADMIN_EMAIL);
        registry.add("memphisreo.bootstrap-admin.password", () -> PLATFORM_ADMIN_PASSWORD);
    }

    @Autowired
    protected RecordingEmailSender emailSender;

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

        /** Замість S3 — файли в пам'яті; "підписане посилання" — фіктивний URL з ключем. */
        @Bean
        @Primary
        InMemoryObjectStorage inMemoryObjectStorage() {
            return new InMemoryObjectStorage();
        }

        /** Замість мережевого Photon — детермінована відповідь. */
        @Bean
        @Primary
        GeocodingProvider fakeGeocodingProvider() {
            return new GeocodingProvider() {
                @Override
                public List<GeoPlace> search(String query, String countryCode, Double lat, Double lon) {
                    return List.of(new GeoPlace("вулиця Хрещатик, 22, Київ", "вулиця Хрещатик", "22", "Київ",
                            "Центр", "Київ", "01001", "UA", 50.4498, 30.5231));
                }

                @Override
                public java.util.Optional<GeoPlace> reverse(double latitude, double longitude) {
                    return java.util.Optional.of(new GeoPlace("вулиця Хрещатик, 22, Київ", "вулиця Хрещатик", "22",
                            "Київ", "Центр", "Київ", "01001", "UA", latitude, longitude));
                }
            };
        }

        /** Замість SMTP — листи складаються в пам'ять, тест читає їх звідти. */
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    protected static class InMemoryObjectStorage implements ObjectStorage {
        final java.util.Map<String, byte[]> objects = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String put(String key, java.io.InputStream content, long contentLength, String contentType) {
            try {
                objects.put(key, content.readAllBytes());
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return key;
        }

        @Override
        public java.io.InputStream get(String key) {
            return new java.io.ByteArrayInputStream(objects.get(key));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        @Override
        public java.net.URI presignedGetUrl(String key, java.time.Duration ttl) {
            return java.net.URI.create("http://storage.test/" + key);
        }
    }

    @Autowired
    protected InMemoryObjectStorage objectStorage;

    protected static class RecordingEmailSender implements EmailSender {
        private final List<Email> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(Email email) {
            sent.add(email);
        }

        public List<Email> sentTo(String address) {
            return sent.stream().filter(e -> e.to().equals(address)).toList();
        }
    }

    protected String platformLogin() {
        ResponseEntity<LoginService.LoginResult> response = restTemplate.postForEntity("/platform-admin/auth/login",
                new AuthController.LoginRequest(PLATFORM_ADMIN_EMAIL, PLATFORM_ADMIN_PASSWORD),
                LoginService.LoginResult.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
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

    /** Повністю заповнена квартира в Києві з ціною — чернетка (DRAFT). */
    protected static PropertyPayload apartmentPayload() {
        PropertyForm form = new PropertyForm(Property.Type.APARTMENT, Property.Market.SECONDARY, "Test property",
                "Світла квартира", new BigDecimal("54.5"), null, null, null, 2, null, 1, 3, 9, 2015, null,
                Property.WallMaterial.BRICK, Property.Condition.EURO, Property.Heating.CENTRAL, null, null, null,
                true, 1, java.util.List.of(Property.Feature.BALCONY), "12",
                new PropertyForm.AddressForm("UA", null, "Kyiv", "Pecherskyi", "Khreshchatyk", "1", "01001", null,
                        50.4501, 30.5234, null));
        return new PropertyPayload(form, new Price(new BigDecimal("95000"), "USD"));
    }

    protected PropertyDetails createProperty(String token) {
        return createProperty(token, apartmentPayload());
    }

    protected PropertyDetails createProperty(String token, PropertyPayload payload) {
        ResponseEntity<PropertyDetails> response = restTemplate.exchange(
                "/api/properties", HttpMethod.POST, authed(token, payload), PropertyDetails.class);
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
