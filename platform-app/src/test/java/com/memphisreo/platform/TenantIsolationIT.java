package com.memphisreo.platform;

import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;

import com.memphisreo.crm.Lead;
import com.memphisreo.crm.LeadActivity;
import com.memphisreo.inquiry.CreateInquiryRequest;
import com.memphisreo.inquiry.Inquiry;
import com.memphisreo.listing.CreateListingRequest;
import com.memphisreo.listing.Listing;
import com.memphisreo.platform.api.LeadController;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.property.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Агенція B не бачить і не змінює даних агенції A — через API (рубіж 1,
 * Hibernate @TenantId) і напряму в БД під логіном застосунку (рубіж 2, RLS;
 * рубіж 3, складені FK). docs/adr/001-shared-schema-rls-cells.md.
 */
class TenantIsolationIT extends AbstractIntegrationTest {

    private RegisterTenantResponse tenantA;
    private String slugA;
    private String tokenA;
    private PropertyDetails propertyA;
    private Listing listingA;
    private Lead leadA;

    private RegisterTenantResponse tenantB;
    private String slugB;
    private String tokenB;

    @BeforeEach
    void twoAgenciesWithData() {
        slugA = "iso-a-" + UUID.randomUUID().toString().substring(0, 8);
        tenantA = register(slugA, "admin@" + slugA + ".ua", "PasswordA123!", "UA");
        tokenA = login("admin@" + slugA + ".ua", "PasswordA123!");
        propertyA = createProperty(tokenA);
        listingA = createListing(tokenA, propertyA.id());

        Inquiry inquiryA = restTemplate.postForEntity(
                "/api/public/tenants/" + slugA + "/listings/" + listingA.getId() + "/inquiries",
                new CreateInquiryRequest("Покупець А", "buyer-a@example.com", null, "Цікавить"),
                Inquiry.class).getBody();
        leadA = restTemplate.exchange("/api/inquiries/" + inquiryA.getId() + "/convert",
                HttpMethod.POST, authed(tokenA), Lead.class).getBody();

        slugB = "iso-b-" + UUID.randomUUID().toString().substring(0, 8);
        tenantB = register(slugB, "admin@" + slugB + ".ua", "PasswordB123!", "UA");
        tokenB = login("admin@" + slugB + ".ua", "PasswordB123!");
    }

    @Test
    void api_otherAgencysRecordsAreInvisible() {
        assertThat(get("/api/properties/" + propertyA.id()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/listings/" + listingA.getId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/leads/" + leadA.getId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/clients/" + leadA.getClientId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(restTemplate.exchange("/api/properties", HttpMethod.GET, authed(tokenB), com.memphisreo.platform.property.PropertyEditorDtos.PropertyCard[].class).getBody())
                .isEmpty();
        assertThat(restTemplate.exchange("/api/listings", HttpMethod.GET, authed(tokenB), Listing[].class).getBody())
                .isEmpty();
        assertThat(restTemplate.exchange("/api/leads", HttpMethod.GET, authed(tokenB), Lead[].class).getBody())
                .isEmpty();
        assertThat(restTemplate.exchange("/api/inquiries", HttpMethod.GET, authed(tokenB), Inquiry[].class).getBody())
                .isEmpty();
        assertThat(restTemplate.exchange("/api/leads/" + leadA.getId() + "/activities",
                HttpMethod.GET, authed(tokenB), LeadActivity[].class).getBody()).isEmpty();
    }

    @Test
    void api_otherAgencysRecordsCannotBeChangedOrReferenced() {
        ResponseEntity<String> statusChange = restTemplate.exchange(
                "/api/leads/" + leadA.getId() + "/status", HttpMethod.PATCH,
                authed(tokenB, new LeadController.UpdateStatusRequest(Lead.Status.LOST)), String.class);
        assertThat(statusChange.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> activity = restTemplate.exchange(
                "/api/leads/" + leadA.getId() + "/activities", HttpMethod.POST,
                authed(tokenB, new LeadController.AddActivityRequest("чужий лід")), String.class);
        assertThat(activity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> listingOnForeignProperty = restTemplate.exchange(
                "/api/listings", HttpMethod.POST,
                authed(tokenB, new CreateListingRequest(propertyA.id(), Listing.DealType.SALE,
                        new BigDecimal("1"), "USD")), String.class);
        assertThat(listingOnForeignProperty.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Публічна заявка: slug агенції B + лістинг агенції A — не знаходиться.
        ResponseEntity<String> crossInquiry = restTemplate.postForEntity(
                "/api/public/tenants/" + slugB + "/listings/" + listingA.getId() + "/inquiries",
                new CreateInquiryRequest("X", "x@example.com", null, null), String.class);
        assertThat(crossInquiry.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Дані A не змінились.
        Lead leadAfter = restTemplate.exchange("/api/leads/" + leadA.getId(), HttpMethod.GET,
                authed(tokenA), Lead.class).getBody();
        assertThat(leadAfter.getStatus()).isEqualTo(Lead.Status.NEW);
    }

    @Test
    void database_appLoginSeesOnlyRowsOfTenantInSession() throws SQLException {
        try (Connection app = appConnection()) {
            // app.tenant_id не виставлено — жодного рядка (fail closed).
            assertThat(count(app, "SELECT count(*) FROM app.property")).isZero();

            setTenant(app, tenantB.tenantId());
            assertThat(count(app, "SELECT count(*) FROM app.property")).isZero();
            assertThat(count(app, "SELECT count(*) FROM app.agent")).isEqualTo(1);

            setTenant(app, tenantA.tenantId());
            assertThat(count(app, "SELECT count(*) FROM app.property WHERE id = '" + propertyA.id() + "'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void database_appLoginCannotWriteRowsOfAnotherTenant() throws SQLException {
        try (Connection app = appConnection()) {
            setTenant(app, tenantB.tenantId());

            // UPDATE чужого рядка: RLS ховає його — 0 змінених рядків.
            try (PreparedStatement update = app.prepareStatement(
                    "UPDATE app.listing SET price = 1 WHERE id = ?")) {
                update.setObject(1, listingA.getId());
                assertThat(update.executeUpdate()).isZero();
            }

            // INSERT з tenant_id іншої агенції — відхиляє WITH CHECK.
            assertThatThrownBy(() -> {
                try (PreparedStatement insert = app.prepareStatement(
                        "INSERT INTO app.role (id, tenant_id, name) VALUES (?, ?, 'Hijack')")) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, tenantA.tenantId());
                    insert.executeUpdate();
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
        }
    }

    @Test
    void database_compositeForeignKeyRejectsCrossTenantReference() throws SQLException {
        // Навіть суперкористувач (обходить RLS) не може прив'язати лістинг
        // агенції B до об'єкта агенції A — рубіж 3 не залежить від RLS.
        try (Connection owner = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThatThrownBy(() -> {
                try (PreparedStatement insert = owner.prepareStatement("""
                        INSERT INTO app.listing (id, tenant_id, property_id, agent_id, deal_type, price, currency)
                        VALUES (?, ?, ?, ?, 'SALE', 1, 'USD')""")) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, tenantB.tenantId());
                    insert.setObject(3, propertyA.id());
                    insert.setObject(4, tenantB.adminAgentId());
                    insert.executeUpdate();
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("foreign key");
        }
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, authed(tokenB), String.class);
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_DB_USER, APP_DB_PASSWORD);
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
