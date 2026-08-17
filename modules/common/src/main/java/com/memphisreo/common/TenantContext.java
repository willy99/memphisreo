package com.memphisreo.common;

/**
 * Поточний tenant запиту, вирішений JWT security-фільтром (docs/security.md §7).
 * Споживається AbstractRoutingDataSource для вибору tenant-схеми (docs/architecture.md §3).
 * tenant_id тут МАЄ походити виключно з підписаного JWT claim — docs/security.md §8.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantInfo> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantInfo tenantInfo) {
        CURRENT.set(tenantInfo);
    }

    public static TenantInfo get() {
        TenantInfo tenantInfo = CURRENT.get();
        if (tenantInfo == null) {
            throw new IllegalStateException("TenantContext не встановлено для поточного запиту");
        }
        return tenantInfo;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record TenantInfo(String tenantId, String schemaName) {
    }
}
