package com.memphisreo.platform;

import com.memphisreo.platform.api.AuthController;
import com.memphisreo.platform.dashboard.TenantStats;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.PropertyRow;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantPage;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantSummary;
import com.memphisreo.platform.registration.RegisterTenantRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Супер-адмін платформи: агенції, їх статистика й об'єкти; окремий контур безпеки (docs/security.md §6). */
class PlatformAdminIT extends AbstractIntegrationTest {

    @Test
    void bootstrapAdmin_canLogIn_wrongPasswordIsRejected() {
        assertThat(platformLogin()).isNotBlank();

        ResponseEntity<String> wrong = restTemplate.postForEntity("/platform-admin/auth/login",
                new AuthController.LoginRequest(PLATFORM_ADMIN_EMAIL, "wrong-password"), String.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listsAgenciesWithStats_andShowsTheirProperties() {
        String slug = "stats-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterTenantResponse agency = register(slug, "admin@" + slug + ".ua", "Password123!", "UA");
        String agencyToken = login("admin@" + slug + ".ua", "Password123!");
        createProperty(agencyToken);
        createProperty(agencyToken);

        String token = platformLogin();
        TenantPage page = restTemplate.exchange("/platform-admin/tenants?size=100", HttpMethod.GET,
                authed(token), TenantPage.class).getBody();
        TenantSummary summary = page.items().stream()
                .filter(t -> t.id().equals(agency.tenantId())).findFirst().orElseThrow();
        assertThat(summary.slug()).isEqualTo(slug);
        assertThat(summary.agents()).isEqualTo(1);
        assertThat(summary.properties()).isEqualTo(2);

        PropertyRow[] properties = restTemplate.exchange("/platform-admin/tenants/" + agency.tenantId() + "/properties",
                HttpMethod.GET, authed(token), PropertyRow[].class).getBody();
        assertThat(properties).hasSize(2);
        assertThat(properties[0].city()).isEqualTo("Kyiv");

        ResponseEntity<String> missing = restTemplate.exchange("/platform-admin/tenants/" + UUID.randomUUID(),
                HttpMethod.GET, authed(token), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createsAgencyWithDefaultAdmin_whoCanLogInAndSeeEmptyDashboard() {
        String slug = "created-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterTenantRequest request = new RegisterTenantRequest(
                "Нова агенція", slug, "UA", "boss@" + slug + ".ua", "Password123!", "Олена", "Коваль");

        ResponseEntity<RegisterTenantResponse> created = restTemplate.exchange("/platform-admin/tenants",
                HttpMethod.POST, authed(platformLogin(), request), RegisterTenantResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        String agencyToken = login("boss@" + slug + ".ua", "Password123!");
        TenantStats stats = restTemplate.exchange("/api/dashboard/stats", HttpMethod.GET,
                authed(agencyToken), TenantStats.class).getBody();
        assertThat(stats).isEqualTo(new TenantStats(1, 0));
    }

    @Test
    void invalidAgencyRequest_isRejectedWith400() {
        RegisterTenantRequest badSlug = new RegisterTenantRequest(
                "X", "Bad Slug!", "UA", "x@example.com", "Password123!", "A", "B");
        ResponseEntity<String> response = restTemplate.exchange("/platform-admin/tenants",
                HttpMethod.POST, authed(platformLogin(), badSlug), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void realmsAreSeparate_tenantTokenCannotUsePlatformApi_andViceVersa() {
        String slug = "realm-" + UUID.randomUUID().toString().substring(0, 8);
        register(slug, "admin@" + slug + ".ua", "Password123!", "UA");
        String agencyToken = login("admin@" + slug + ".ua", "Password123!");

        assertThat(restTemplate.exchange("/platform-admin/tenants", HttpMethod.GET, authed(agencyToken),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/api/properties", HttpMethod.GET, authed(platformLogin()),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange("/platform-admin/tenants", HttpMethod.GET, HttpEntity.EMPTY,
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
