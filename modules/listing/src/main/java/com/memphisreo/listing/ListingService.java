package com.memphisreo.listing;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Продаж об'єкта (лістинг). Один "поточний" лістинг на об'єкт: усі, крім
 * SOLD. property_id/seller_client_id — сирі посилання через межі модулів.
 * Переходи статусів — лише через методи нижче (docs/domain-model.md §6.0).
 */
@Service
public class ListingService {

    private static final Set<Listing.Status> FINISHED = EnumSet.of(Listing.Status.SOLD);
    private static final Set<String> CURRENCIES = Set.of("USD", "UAH", "EUR");
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000000000");

    /** Результат зміни стану: що саме сталося — для таймлайну в композиційному корені. */
    public record Change(Listing listing, String event, Map<String, Object> details) {
    }

    private final ListingRepository listingRepository;
    private final ListingPriceHistoryRepository priceHistoryRepository;

    public ListingService(ListingRepository listingRepository, ListingPriceHistoryRepository priceHistoryRepository) {
        this.listingRepository = listingRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    public Optional<Listing> current(UUID propertyId) {
        return listingRepository.findFirstByPropertyIdAndStatusNotInOrderByCreatedAtDesc(propertyId, FINISHED);
    }

    public Listing require(UUID propertyId) {
        return current(propertyId).orElseThrow(() -> new NotFoundException("Продаж для об'єкта ще не заведено: " + propertyId));
    }

    /** Останній лістинг об'єкта, включно з проданим — для вкладки "Продаж" після закриття. */
    public Optional<Listing> latest(UUID propertyId) {
        return listingRepository.findFirstByPropertyIdOrderByCreatedAtDesc(propertyId);
    }

    public Map<UUID, Listing> currentByProperty(Collection<UUID> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        return listingRepository.findByPropertyIdInAndStatusNotIn(propertyIds, FINISHED).stream()
                .collect(Collectors.toMap(Listing::getPropertyId, Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    public List<Listing> activeForSeller(UUID sellerClientId) {
        return listingRepository.findBySellerClientIdOrderByCreatedAtDesc(sellerClientId);
    }

    public List<ListingPriceHistory> priceHistory(UUID listingId) {
        return priceHistoryRepository.findByListingIdOrderByChangedAtDesc(listingId);
    }

    /** Чернетка продажу створюється разом з об'єктом; ціна може бути порожньою. */
    @Transactional
    public Listing ensureDraft(UUID tenantId, UUID propertyId, UUID agentId) {
        return current(propertyId).orElseGet(() -> {
            Listing draft = new Listing();
            draft.setTenantId(tenantId);
            draft.setPropertyId(propertyId);
            draft.setAgentId(agentId);
            draft.setDealType(Listing.DealType.SALE);
            draft.setCurrency("USD");
            draft.setStatus(Listing.Status.DRAFT);
            return listingRepository.save(draft);
        });
    }

    /** Ціна з форми об'єкта (швидкий шлях). Порожня ціна нічого не змінює. */
    @Transactional
    public Optional<Change> setAskingPrice(UUID tenantId, UUID propertyId, UUID agentId, BigDecimal price, String currency) {
        if (price == null) {
            return Optional.empty();
        }
        validatePrice(price, currency);
        Listing listing = ensureDraft(tenantId, propertyId, agentId);
        return applyPrice(listing, price, currency);
    }

    @Transactional
    public List<Change> updateSale(UUID propertyId, SaleForm form) {
        Listing listing = require(propertyId);
        List<FieldError> errors = new ArrayList<>();
        if (form.price() != null) {
            errors.addAll(priceErrors(form.price(), form.currency()));
        }
        if (form.commissionPercent() != null
                && (form.commissionPercent().signum() < 0 || form.commissionPercent().compareTo(new BigDecimal("50")) > 0)) {
            errors.add(new FieldError("commissionPercent", "outOfRange"));
        }
        if (form.commissionFixed() != null && form.commissionFixed().signum() < 0) {
            errors.add(new FieldError("commissionFixed", "positive"));
        }
        if (form.accessNotes() != null && form.accessNotes().length() > 500) {
            errors.add(new FieldError("accessNotes", "tooLong"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Некоректні дані продажу", errors);
        }

        List<Change> changes = new ArrayList<>();
        if (form.price() != null) {
            applyPrice(listing, form.price(), form.currency()).ifPresent(changes::add);
        }
        boolean sellerChanged = !java.util.Objects.equals(listing.getSellerClientId(), form.sellerClientId());
        listing.setSellerClientId(form.sellerClientId());
        listing.setMandateType(form.mandateType());
        listing.setMandateValidUntil(form.mandateValidUntil());
        listing.setCommissionPercent(form.commissionPercent());
        listing.setCommissionFixed(form.commissionFixed());
        listing.setAccessNotes(trim(form.accessNotes()));
        listing.setHideExactAddress(form.hideExactAddress());
        listing.setUpdatedAt(Instant.now());
        if (sellerChanged && form.sellerClientId() != null) {
            changes.add(new Change(listing, "SELLER_SET", Map.of("sellerClientId", form.sellerClientId().toString())));
        }
        return changes;
    }

    /** DRAFT/WITHDRAWN/EXPIRED → ACTIVE. Потребує ціни; мандат, якщо вказано, не має бути протермінований. */
    @Transactional
    public Change activate(UUID propertyId) {
        Listing listing = require(propertyId);
        requireStatus(listing, Listing.Status.DRAFT, Listing.Status.WITHDRAWN, Listing.Status.EXPIRED);
        List<FieldError> missing = new ArrayList<>();
        if (listing.getPrice() == null) {
            missing.add(new FieldError("price", "required"));
        }
        if (listing.getMandateValidUntil() != null && listing.getMandateValidUntil().isBefore(LocalDate.now())) {
            missing.add(new FieldError("mandateValidUntil", "mandateExpired"));
        }
        if (!missing.isEmpty()) {
            throw new ValidationException("Не можна виставити на продаж", missing);
        }
        boolean first = listing.getPublishedAt() == null;
        listing.setStatus(Listing.Status.ACTIVE);
        listing.setWithdrawnReason(null);
        if (first) {
            listing.setPublishedAt(Instant.now());
        }
        listing.setUpdatedAt(Instant.now());
        return new Change(listing, first ? "LISTED" : "RELISTED", Map.of());
    }

    /** ACTIVE → WITHDRAWN (знято з продажу агентом/власником). */
    @Transactional
    public Change withdraw(UUID propertyId, String reason) {
        Listing listing = require(propertyId);
        requireStatus(listing, Listing.Status.ACTIVE);
        listing.setStatus(Listing.Status.WITHDRAWN);
        listing.setWithdrawnReason(trim(reason));
        listing.setUpdatedAt(Instant.now());
        return new Change(listing, "WITHDRAWN", reason == null || reason.isBlank() ? Map.of() : Map.of("reason", reason.trim()));
    }

    /** ACTIVE → UNDER_OFFER (офер прийнято) — викликає модуль угод. */
    @Transactional
    public Change markUnderOffer(UUID propertyId) {
        Listing listing = require(propertyId);
        requireStatus(listing, Listing.Status.ACTIVE);
        listing.setStatus(Listing.Status.UNDER_OFFER);
        listing.setUpdatedAt(Instant.now());
        return new Change(listing, "UNDER_OFFER", Map.of());
    }

    /** UNDER_OFFER → ACTIVE (угоду скасовано). */
    @Transactional
    public Change backToMarket(UUID propertyId) {
        Listing listing = require(propertyId);
        requireStatus(listing, Listing.Status.UNDER_OFFER);
        listing.setStatus(Listing.Status.ACTIVE);
        listing.setUpdatedAt(Instant.now());
        return new Change(listing, "BACK_TO_MARKET", Map.of());
    }

    /** UNDER_OFFER (або ACTIVE — продано поза системою) → SOLD. */
    @Transactional
    public Change markSold(UUID propertyId, BigDecimal finalPrice) {
        Listing listing = require(propertyId);
        requireStatus(listing, Listing.Status.UNDER_OFFER, Listing.Status.ACTIVE);
        if (finalPrice != null) {
            validatePrice(finalPrice, listing.getCurrency());
            listing.setPrice(finalPrice);
        }
        listing.setStatus(Listing.Status.SOLD);
        listing.setClosedAt(Instant.now());
        listing.setUpdatedAt(Instant.now());
        return new Change(listing, "SOLD", finalPrice == null ? Map.of() : Map.of("finalPrice", finalPrice.toPlainString()));
    }

    /** Щоденний job: ACTIVE з протермінованим мандатом → EXPIRED. */
    @Transactional
    public List<Change> expireMandates(LocalDate today) {
        List<Change> changes = new ArrayList<>();
        for (Listing listing : listingRepository.findByStatusAndMandateValidUntilBefore(Listing.Status.ACTIVE, today)) {
            listing.setStatus(Listing.Status.EXPIRED);
            listing.setUpdatedAt(Instant.now());
            changes.add(new Change(listing, "MANDATE_EXPIRED", Map.of("validUntil", listing.getMandateValidUntil().toString())));
        }
        return changes;
    }

    private Optional<Change> applyPrice(Listing listing, BigDecimal price, String currency) {
        BigDecimal previous = listing.getPrice();
        String previousCurrency = listing.getCurrency();
        boolean changed = previous == null || previous.compareTo(price) != 0 || !currency.equals(previousCurrency);
        if (!changed) {
            return Optional.empty();
        }
        listing.setPrice(price);
        listing.setCurrency(currency);
        listing.setUpdatedAt(Instant.now());
        listingRepository.save(listing);
        ListingPriceHistory history = new ListingPriceHistory();
        history.setListingId(listing.getId());
        history.setPrice(price);
        history.setCurrency(currency);
        priceHistoryRepository.save(history);
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("price", price.toPlainString());
        details.put("currency", currency);
        if (previous != null) {
            details.put("previousPrice", previous.toPlainString());
            details.put("previousCurrency", previousCurrency);
        }
        return Optional.of(new Change(listing, previous == null ? "PRICE_SET" : "PRICE_CHANGED", details));
    }

    private static void requireStatus(Listing listing, Listing.Status... allowed) {
        for (Listing.Status status : allowed) {
            if (listing.getStatus() == status) {
                return;
            }
        }
        throw new ValidationException("Неможливий перехід зі статусу " + listing.getStatus(),
                List.of(new FieldError("status", "invalidTransition")));
    }

    private static void validatePrice(BigDecimal price, String currency) {
        List<FieldError> errors = priceErrors(price, currency);
        if (!errors.isEmpty()) {
            throw new ValidationException("Некоректна ціна", errors);
        }
    }

    private static List<FieldError> priceErrors(BigDecimal price, String currency) {
        List<FieldError> errors = new ArrayList<>();
        if (price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            errors.add(new FieldError("price", "outOfRange"));
        }
        if (currency == null || !CURRENCIES.contains(currency)) {
            errors.add(new FieldError("currency", "invalid"));
        }
        return errors;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
