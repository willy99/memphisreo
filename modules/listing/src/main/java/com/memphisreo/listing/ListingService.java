package com.memphisreo.listing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** property_id — сире посилання, не перевіряється тут (межа модуля з property). */
@Service
public class ListingService {

    /** Лістинги, що вже не "поточні" для об'єкта — історія. */
    private static final Set<Listing.Status> FINISHED = EnumSet.of(Listing.Status.CLOSED, Listing.Status.ARCHIVED);

    private final ListingRepository listingRepository;
    private final ListingPriceHistoryRepository priceHistoryRepository;

    public ListingService(ListingRepository listingRepository, ListingPriceHistoryRepository priceHistoryRepository) {
        this.listingRepository = listingRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    /** Поточний (не закритий/архівний) лістинг об'єкта. */
    public Optional<Listing> current(UUID propertyId) {
        return listingRepository.findFirstByPropertyIdAndStatusNotInOrderByCreatedAtDesc(propertyId, FINISHED);
    }

    public Map<UUID, Listing> currentByProperty(Collection<UUID> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        return listingRepository.findByPropertyIdInAndStatusNotIn(propertyIds, FINISHED).stream()
                .collect(Collectors.toMap(Listing::getPropertyId, Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    /**
     * Ціна з форми об'єкта: оновлює поточний лістинг або створює чернетку
     * продажу. Зміна ціни пишеться в історію. Порожня ціна нічого не змінює.
     */
    @Transactional
    public Optional<Listing> setAskingPrice(UUID tenantId, UUID propertyId, UUID agentId,
                                            BigDecimal price, String currency) {
        if (price == null) {
            return current(propertyId);
        }
        Listing listing = current(propertyId).orElseGet(() -> {
            Listing draft = new Listing();
            draft.setTenantId(tenantId);
            draft.setPropertyId(propertyId);
            draft.setAgentId(agentId);
            draft.setDealType(Listing.DealType.SALE);
            draft.setStatus(Listing.Status.DRAFT);
            return draft;
        });
        boolean changed = listing.getPrice() == null || listing.getPrice().compareTo(price) != 0
                || !currency.equals(listing.getCurrency());
        listing.setPrice(price);
        listing.setCurrency(currency);
        listing.setUpdatedAt(Instant.now());
        listing = listingRepository.save(listing);
        if (changed) {
            ListingPriceHistory history = new ListingPriceHistory();
            history.setListingId(listing.getId());
            history.setPrice(price);
            history.setCurrency(currency);
            priceHistoryRepository.save(history);
        }
        return Optional.of(listing);
    }

    public Listing create(UUID tenantId, UUID agentId, CreateListingRequest request) {
        Listing listing = new Listing();
        listing.setPropertyId(request.propertyId());
        listing.setAgentId(agentId);
        listing.setDealType(request.dealType());
        listing.setPrice(request.price());
        listing.setCurrency(request.currency());
        listing.setStatus(Listing.Status.PUBLISHED);
        listing.setTenantId(tenantId);
        listing.setPublishedAt(Instant.now());

        return listingRepository.save(listing);
    }
}
