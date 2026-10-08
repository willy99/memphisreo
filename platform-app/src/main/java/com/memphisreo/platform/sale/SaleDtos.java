package com.memphisreo.platform.sale;

import com.memphisreo.activity.ActivityEvent;
import com.memphisreo.listing.Listing;
import com.memphisreo.property.PropertyAgent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO вкладки "Продаж", агентів і таймлайну. */
public final class SaleDtos {

    private SaleDtos() {
    }

    public record SellerView(UUID id, String firstName, String lastName, String phone, String email) {
    }

    public record AgentView(UUID id, String firstName, String lastName, String phone, String email, PropertyAgent.Role role) {
    }

    public record PricePoint(BigDecimal price, String currency, Instant changedAt) {
    }

    public record SaleView(
            UUID listingId,
            Listing.Status status,
            BigDecimal price,
            String currency,
            SellerView seller,
            Listing.MandateType mandateType,
            LocalDate mandateValidUntil,
            BigDecimal commissionPercent,
            BigDecimal commissionFixed,
            String accessNotes,
            boolean hideExactAddress,
            String withdrawnReason,
            Instant publishedAt,
            Instant closedAt,
            List<PricePoint> priceHistory,
            List<AgentView> agents,
            /** Публічне посилання на об'єкт (null, поки не ACTIVE). */
            String publicUrl,
            /** Чого бракує, щоб виставити на продаж. */
            List<String> blockers
    ) {
    }

    public record WithdrawRequest(String reason) {
    }

    public record SoldRequest(BigDecimal finalPrice) {
    }

    public record AgentsRequest(UUID leadAgentId, List<UUID> coAgentIds) {
    }

    public record TimelineEntry(UUID id, String type, Map<String, Object> payload, AgentView actor,
                                boolean ownerVisible, Instant createdAt) {
    }

    public record OwnerLink(String url, Instant expiresAt) {
    }

    public static TimelineEntry entry(ActivityEvent e, AgentView actor) {
        return new TimelineEntry(e.getId(), e.getType(), e.getPayload(), actor, e.isOwnerVisible(), e.getCreatedAt());
    }
}
