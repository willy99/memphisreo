package com.memphisreo.platform;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Інваріанти рубежу 2 з ADR-001, що мусять триматись для КОЖНОЇ майбутньої
 * таблиці: нова таблиця без tenant_id/RLS-політики ламає збірку, а не
 * тихо відкриває дані всіх агенцій. Перевіряється каталог Postgres
 * після прогону всіх міграцій.
 */
class RowLevelSecurityInvariantIT extends AbstractIntegrationTest {

    @Test
    void everyAppTable_hasTenantIdColumn() throws SQLException {
        List<String> violations = queryList("""
                SELECT c.relname FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND c.relkind = 'r'
                  AND NOT EXISTS (SELECT 1 FROM pg_attribute a
                                  WHERE a.attrelid = c.oid AND a.attname = 'tenant_id'
                                    AND NOT a.attisdropped AND a.attnotnull)
                """);
        assertThat(violations).as("таблиці схеми app без tenant_id NOT NULL").isEmpty();
    }

    @Test
    void everyAppTable_hasForcedRowLevelSecurityWithTenantPolicy() throws SQLException {
        List<String> violations = queryList("""
                SELECT c.relname FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND c.relkind = 'r'
                  AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity
                       OR NOT EXISTS (SELECT 1 FROM pg_policies p
                                      WHERE p.schemaname = 'app' AND p.tablename = c.relname
                                        AND p.policyname = 'tenant_isolation'))
                """);
        assertThat(violations).as("таблиці схеми app без FORCE RLS і політики tenant_isolation").isEmpty();
    }

    @Test
    void everyAppTable_hasCompositeTenantKeyForForeignKeys() throws SQLException {
        List<String> violations = queryList("""
                SELECT c.relname FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND c.relkind = 'r'
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_constraint k
                      WHERE k.conrelid = c.oid AND k.contype IN ('u', 'p')
                        AND k.conkey = ARRAY[
                            (SELECT attnum FROM pg_attribute WHERE attrelid = c.oid AND attname = 'tenant_id'),
                            (SELECT attnum FROM pg_attribute WHERE attrelid = c.oid AND attname = 'id')]::int2[])
                """);
        assertThat(violations).as("таблиці схеми app без UNIQUE (tenant_id, id)").isEmpty();
    }

    @Test
    void everyForeignKeyBetweenAppTables_includesTenantId() throws SQLException {
        List<String> violations = queryList("""
                SELECT k.conname FROM pg_constraint k
                JOIN pg_class c ON c.oid = k.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND k.contype = 'f'
                  AND NOT (SELECT attnum FROM pg_attribute WHERE attrelid = c.oid AND attname = 'tenant_id')
                          = ANY (k.conkey)
                """);
        assertThat(violations).as("FK без tenant_id — можна послатись на запис іншої агенції").isEmpty();
    }

    @Test
    void applicationLogin_cannotBypassRowLevelSecurity() throws SQLException {
        List<String> flags = queryList("""
                SELECT rolname || ' super=' || rolsuper || ' bypassrls=' || rolbypassrls
                FROM pg_roles WHERE rolname = '%s'
                """.formatted(APP_DB_USER));
        assertThat(flags).containsExactly(APP_DB_USER + " super=false bypassrls=false");

        List<String> ownedByApp = queryList("""
                SELECT c.relname FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'app' AND pg_get_userbyid(c.relowner) IN ('%s', 'memphisreo_app')
                """.formatted(APP_DB_USER));
        assertThat(ownedByApp).as("власник таблиць обходить RLS без FORCE — застосунок не має ним бути").isEmpty();
    }

    private static List<String> queryList(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            List<String> result = new ArrayList<>();
            while (rs.next()) {
                result.add(rs.getString(1));
            }
            return result;
        }
    }
}
