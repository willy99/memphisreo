package com.memphisreo.platform;

import com.memphisreo.platform.dashboard.TenantStats;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Статистика головної сторінки рахує лише свою агенцію. */
class DashboardIT extends AbstractIntegrationTest {

    @Test
    void statsCountOnlyOwnAgency() {
        String slugA = "dash-a-" + UUID.randomUUID().toString().substring(0, 8);
        register(slugA, "admin@" + slugA + ".ua", "Password123!", "UA");
        String tokenA = login("admin@" + slugA + ".ua", "Password123!");
        createProperty(tokenA);

        String slugB = "dash-b-" + UUID.randomUUID().toString().substring(0, 8);
        register(slugB, "admin@" + slugB + ".ua", "Password123!", "UA");
        String tokenB = login("admin@" + slugB + ".ua", "Password123!");

        assertThat(stats(tokenA)).isEqualTo(new TenantStats(1, 1));
        assertThat(stats(tokenB)).isEqualTo(new TenantStats(1, 0));
    }

    private TenantStats stats(String token) {
        return restTemplate.exchange("/api/dashboard/stats", HttpMethod.GET, authed(token), TenantStats.class).getBody();
    }
}
