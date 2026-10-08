package com.memphisreo.platform.crm;

import com.memphisreo.common.TenantContext;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientRepository;
import com.memphisreo.crm.ClientRequirement;
import com.memphisreo.crm.ClientService;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingRepository;
import com.memphisreo.media.MediaService;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyCard;
import com.memphisreo.platform.property.PropertyEditorService;
import com.memphisreo.property.Address;
import com.memphisreo.property.AddressRepository;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Підбір "об'єкт ↔ клієнт" за запитом клієнта: жорсткі умови (тип, ціна,
 * кімнати, площа, район, ринок, обов'язкові зручності) — усі мають
 * збігатися; score — скільки з заданих умов узагалі перевірялось, щоб
 * точніші запити були вище. Без ML: SQL-подібна фільтрація в пам'яті
 * агенції (сотні об'єктів), docs/sales-workflow.md §3.5.
 */
@Service
public class MatchingService {

    public record ClientMatch(UUID clientId, String firstName, String lastName, String phone, int score, List<String> matched) {
    }

    public record PropertyMatch(PropertyCard property, int score, List<String> matched) {
    }

    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final ListingRepository listingRepository;
    private final PropertyRepository propertyRepository;
    private final AddressRepository addressRepository;
    private final PropertyEditorService editorService;
    private final MediaService mediaService;

    public MatchingService(ClientService clientService, ClientRepository clientRepository,
                           ListingRepository listingRepository, PropertyRepository propertyRepository,
                           AddressRepository addressRepository, PropertyEditorService editorService,
                           MediaService mediaService) {
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.listingRepository = listingRepository;
        this.propertyRepository = propertyRepository;
        this.addressRepository = addressRepository;
        this.editorService = editorService;
        this.mediaService = mediaService;
    }

    /** Об'єкти у продажу, що підходять під запит клієнта. */
    public List<PropertyMatch> propertiesForClient(UUID clientId) {
        ClientRequirement r = clientService.requirement(clientId).filter(ClientRequirement::isActive).orElse(null);
        if (r == null) {
            return List.of();
        }
        List<PropertyMatch> result = new ArrayList<>();
        for (PropertyCard card : editorService.cards()) {
            if (card.status() == Property.Status.ARCHIVED) {
                continue;
            }
            Listing listing = listingRepository.findFirstByPropertyIdOrderByCreatedAtDesc(card.id()).orElse(null);
            if (listing == null || listing.getStatus() != Listing.Status.ACTIVE) {
                continue;
            }
            Property p = propertyRepository.findById(card.id()).orElse(null);
            if (p == null) {
                continue;
            }
            List<String> matched = match(r, p, card.district(), card.city(), listing);
            if (matched != null) {
                result.add(new PropertyMatch(card, matched.size(), matched));
            }
        }
        result.sort(Comparator.comparingInt(PropertyMatch::score).reversed());
        return result;
    }

    /** Клієнти з активним запитом, яким підходить цей об'єкт. */
    public List<ClientMatch> clientsForProperty(UUID propertyId) {
        Property p = propertyRepository.findById(propertyId).orElse(null);
        Listing listing = listingRepository.findFirstByPropertyIdOrderByCreatedAtDesc(propertyId).orElse(null);
        if (p == null || listing == null || listing.getStatus() == Listing.Status.SOLD) {
            return List.of();
        }
        Address a = addressRepository.findById(p.getAddressId()).orElse(null);
        List<ClientRequirement> requirements = clientService.activeRequirements();
        Map<UUID, Client> clients = clientRepository.findAllById(requirements.stream().map(ClientRequirement::getClientId).toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        List<ClientMatch> result = new ArrayList<>();
        for (ClientRequirement r : requirements) {
            Client c = clients.get(r.getClientId());
            if (c == null) {
                continue;
            }
            List<String> matched = match(r, p, a == null ? null : a.getDistrict(), a == null ? null : a.getCity(), listing);
            if (matched != null) {
                result.add(new ClientMatch(c.getId(), c.getFirstName(), c.getLastName(), c.getPhone(), matched.size(), matched));
            }
        }
        result.sort(Comparator.comparingInt(ClientMatch::score).reversed());
        return result;
    }

    /** @return перелік умов, що збіглися, або null, якщо хоч одна задана умова не виконана. */
    static List<String> match(ClientRequirement r, Property p, String district, String city, Listing listing) {
        List<String> matched = new ArrayList<>();
        if (r.getPropertyType() != null) {
            if (!r.getPropertyType().equals(p.getType().name())) {
                return null;
            }
            matched.add("type");
        }
        if (r.getRoomsMin() != null || r.getRoomsMax() != null) {
            Integer rooms = p.getRooms();
            if (rooms == null || (r.getRoomsMin() != null && rooms < r.getRoomsMin())
                    || (r.getRoomsMax() != null && rooms > r.getRoomsMax())) {
                return null;
            }
            matched.add("rooms");
        }
        if (r.getPriceMin() != null || r.getPriceMax() != null) {
            BigDecimal price = listing.getPrice();
            // Різні валюти не порівнюємо — вважаємо, що не підходить (конвертація — пізніше).
            if (price == null || !Objects.equals(listing.getCurrency(), r.getCurrency())
                    || (r.getPriceMin() != null && price.compareTo(r.getPriceMin()) < 0)
                    || (r.getPriceMax() != null && price.compareTo(r.getPriceMax()) > 0)) {
                return null;
            }
            matched.add("price");
        }
        if (r.getAreaMin() != null) {
            BigDecimal area = p.getType() == Property.Type.LAND ? p.getLandAreaSqm() : p.getAreaSqm();
            if (area == null || area.compareTo(r.getAreaMin()) < 0) {
                return null;
            }
            matched.add("area");
        }
        if (!r.getDistricts().isEmpty()) {
            boolean hit = r.getDistricts().stream().anyMatch(d -> equalsIgnoreCase(d, district) || equalsIgnoreCase(d, city));
            if (!hit) {
                return null;
            }
            matched.add("district");
        }
        if (r.getMarket() != null) {
            if (p.getMarket() == null || !r.getMarket().equals(p.getMarket().name())) {
                return null;
            }
            matched.add("market");
        }
        if (!r.getMustHave().isEmpty()) {
            List<String> features = p.getFeatures().stream().map(Enum::name).toList();
            if (!features.containsAll(r.getMustHave())) {
                return null;
            }
            matched.add("features");
        }
        return matched;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
