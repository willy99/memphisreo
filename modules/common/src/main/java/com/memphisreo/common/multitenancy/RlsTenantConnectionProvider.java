package com.memphisreo.common.multitenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Рубіж 2 з ADR-001: кожне з'єднання, яке Hibernate бере для сесії tenant-а,
 * отримує {@code app.tenant_id} для RLS-політик, а при поверненні в пул —
 * скидається. Значення береться з tenant-а тієї самої сесії, що і фільтр
 * {@code @TenantId}, — обидва рубежі завжди бачать одного tenant-а.
 *
 * Саме рівень з'єднання, а не початок транзакції: Spring Data виконує
 * власні query-методи репозиторіїв без транзакції, і хук на транзакції
 * їх би пропустив (RLS тоді повертає 0 рядків — fail closed, але не робоче).
 * Значення виставляється при КОЖНІЙ видачі з'єднання, тож навіть невдалий
 * скид не "протече" до наступного tenant-а.
 */
@Component
public class RlsTenantConnectionProvider implements MultiTenantConnectionProvider<UUID> {

    private static final String SET_TENANT = "SELECT set_config('app.tenant_id', ?, false)";

    private final DataSource dataSource;

    public RlsTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(UUID tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        try {
            setTenant(connection, tenantIdentifier.toString());
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(UUID tenantIdentifier, Connection connection) throws SQLException {
        try {
            setTenant(connection, "");
        } finally {
            releaseAnyConnection(connection);
        }
    }

    private static void setTenant(Connection connection, String tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
            statement.setString(1, tenantId);
            statement.execute();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException();
    }
}
