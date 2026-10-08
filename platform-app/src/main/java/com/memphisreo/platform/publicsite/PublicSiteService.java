package com.memphisreo.platform.publicsite;

import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.agent.Agent;
import com.memphisreo.agent.AgentRepository;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import com.memphisreo.inquiry.Inquiry;
import com.memphisreo.inquiry.InquiryService;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingRepository;
import com.memphisreo.listing.ListingService;
import com.memphisreo.media.MediaService;
import com.memphisreo.media.MediaView;
import com.memphisreo.platform.publicsite.PublicDtos.*;
import com.memphisreo.property.Address;
import com.memphisreo.property.AddressRepository;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyAgent;
import com.memphisreo.property.PropertyAgentRepository;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Публічна сторінка агенції: tenant — зі slug у шляху, дані читаються від
 * імені агенції ({@link TenantContext#callAs}) під тими самими RLS-правилами.
 * Показуються лише об'єкти з лістингом ACTIVE. Точна адреса ховається, якщо
 * агент так налаштував (hideExactAddress): без вулиці/будинку, координати
 * округлені на ~300 м.
 */
@Service
public class PublicSiteService {

    public record Filters(Property.Type type, Integer roomsMin, BigDecimal priceMin, BigDecimal priceMax,
                          BigDecimal areaMin, BigDecimal areaMax, String district, Property.Market market,
                          List<Property.Feature> features, String q, String sort, int page, int size) {
    }

    private static final int MAX_SIZE = 60;

    private final TenantRepository tenantRepository;
    private final ListingRepository listingRepository;
    private final ListingService listingService;
    private final PropertyRepository propertyRepository;
    private final AddressRepository addressRepository;
    private final PropertyAgentRepository propertyAgentRepository;
    private final AgentRepository agentRepository;
    private final MediaService mediaService;
    private final InquiryService inquiryService;
    private final ActivityRecorder activity;
    private final TransactionTemplate tx;

    public PublicSiteService(TenantRepository tenantRepository, ListingRepository listingRepository,
                             ListingService listingService, PropertyRepository propertyRepository,
                             AddressRepository addressRepository, PropertyAgentRepository propertyAgentRepository,
                             AgentRepository agentRepository, MediaService mediaService, InquiryService inquiryService,
                             ActivityRecorder activity, TransactionTemplate tx) {
        this.tenantRepository = tenantRepository;
        this.listingRepository = listingRepository;
        this.listingService = listingService;
        this.propertyRepository = propertyRepository;
        this.addressRepository = addressRepository;
        this.propertyAgentRepository = propertyAgentRepository;
        this.agentRepository = agentRepository;
        this.mediaService = mediaService;
        this.inquiryService = inquiryService;
        this.activity = activity;
        this.tx = tx;
    }

    public Tenant agency(String slug) {
        return tenantRepository.findBySlug(slug)
                .filter(t -> t.getStatus() != Tenant.Status.SUSPENDED && t.getStatus() != Tenant.Status.CHURNED)
                .orElseThrow(() -> new NotFoundException("Агенцію не знайдено: " + slug));
    }

    public AgencyInfo info(String slug) {
        Tenant tenant = agency(slug);
        long active = TenantContext.callAs(tenant.getId(), () -> listingRepository.findByStatus(Listing.Status.ACTIVE).size());
        return info(tenant, active);
    }

    public SearchResult search(String slug, Filters f) {
        Tenant tenant = agency(slug);
        return TenantContext.callAs(tenant.getId(), () -> {
            List<PublicCard> all = activeCards();
            Set<String> districts = all.stream().map(PublicCard::district).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(TreeSet::new));
            BigDecimal minPrice = all.stream().map(PublicCard::price).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal maxPrice = all.stream().map(PublicCard::price).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

            Map<UUID, Property> byId = propertyRepository.findAllById(all.stream().map(PublicCard::id).toList()).stream()
                    .collect(Collectors.toMap(Property::getId, Function.identity()));
            String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
            Stream<PublicCard> stream = all.stream()
                    .filter(c -> f.type() == null || c.type() == f.type())
                    .filter(c -> f.roomsMin() == null || (c.rooms() != null && c.rooms() >= f.roomsMin()))
                    .filter(c -> f.priceMin() == null || (c.price() != null && c.price().compareTo(f.priceMin()) >= 0))
                    .filter(c -> f.priceMax() == null || (c.price() != null && c.price().compareTo(f.priceMax()) <= 0))
                    .filter(c -> f.areaMin() == null || (c.areaSqm() != null && c.areaSqm().compareTo(f.areaMin()) >= 0))
                    .filter(c -> f.areaMax() == null || (c.areaSqm() != null && c.areaSqm().compareTo(f.areaMax()) <= 0))
                    .filter(c -> f.district() == null || f.district().isBlank() || f.district().equalsIgnoreCase(c.district()))
                    .filter(c -> f.market() == null || byId.get(c.id()).getMarket() == f.market())
                    .filter(c -> f.features() == null || f.features().isEmpty()
                            || byId.get(c.id()).getFeatures().containsAll(f.features()))
                    .filter(c -> q.isEmpty() || Stream.of(c.title(), c.district(), c.street(), c.complexName(),
                            byId.get(c.id()).getDescription()).filter(Objects::nonNull)
                            .anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(q)));
            Comparator<PublicCard> order = switch (f.sort() == null ? "newest" : f.sort()) {
                case "priceAsc" -> Comparator.comparing(PublicCard::price, Comparator.nullsLast(Comparator.naturalOrder()));
                case "priceDesc" -> Comparator.comparing(PublicCard::price, Comparator.nullsLast(Comparator.reverseOrder()));
                case "areaDesc" -> Comparator.comparing(PublicCard::areaSqm, Comparator.nullsLast(Comparator.reverseOrder()));
                default -> Comparator.comparing(PublicCard::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            };
            List<PublicCard> filtered = stream.sorted(order).toList();
            int size = Math.min(Math.max(f.size(), 1), MAX_SIZE);
            int page = Math.max(f.page(), 0);
            List<PublicCard> items = filtered.stream().skip((long) page * size).limit(size).toList();
            return new SearchResult(items, filtered.size(), page, size, new ArrayList<>(districts), minPrice, maxPrice);
        });
    }

    public PublicDetails details(String slug, UUID propertyId) {
        Tenant tenant = agency(slug);
        return TenantContext.callAs(tenant.getId(), () -> {
            Listing listing = listingService.current(propertyId)
                    .filter(l -> l.getStatus() == Listing.Status.ACTIVE)
                    .orElseThrow(() -> new NotFoundException("Об'єкт не знайдено"));
            Property p = propertyRepository.findById(propertyId).orElseThrow(() -> new NotFoundException("Об'єкт не знайдено"));
            Address a = addressRepository.findById(p.getAddressId()).orElseThrow(() -> new NotFoundException("Адресу не знайдено"));
            List<MediaView> media = mediaService.list(propertyId);
            List<PublicPhoto> photos = media.stream().filter(m -> m.kind() == com.memphisreo.media.PropertyMedia.Kind.PHOTO)
                    .sorted(Comparator.comparing(MediaView::cover).reversed().thenComparing(MediaView::position))
                    .map(m -> new PublicPhoto(m.url(), m.thumbUrl(), m.caption())).toList();
            List<PublicPhoto> plans = media.stream().filter(m -> m.kind() == com.memphisreo.media.PropertyMedia.Kind.FLOORPLAN)
                    .map(m -> new PublicPhoto(m.url(), m.thumbUrl(), m.caption())).toList();
            List<String> videos = media.stream().filter(m -> m.kind() == com.memphisreo.media.PropertyMedia.Kind.VIDEO_LINK
                    || m.kind() == com.memphisreo.media.PropertyMedia.Kind.VIDEO)
                    .map(m -> m.externalUrl() != null ? m.externalUrl() : m.url()).filter(Objects::nonNull).toList();
            List<PublicAgent> agents = propertyAgentRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId).stream()
                    .sorted(Comparator.comparing((PropertyAgent l) -> l.getRole() != PropertyAgent.Role.LEAD))
                    .map(PropertyAgent::getAgentId).map(agentRepository::findById).flatMap(java.util.Optional::stream)
                    .filter(ag -> ag.getStatus() == Agent.Status.ACTIVE)
                    .map(ag -> new PublicAgent(ag.getFirstName(), ag.getLastName(), ag.getPhone(), ag.getEmail())).toList();
            boolean hide = listing.isHideExactAddress();
            double[] geo = coordinates(a, hide);
            List<PublicCard> similar = activeCards().stream()
                    .filter(c -> !c.id().equals(propertyId) && c.type() == p.getType()).limit(3).toList();
            long active = listingRepository.findByStatus(Listing.Status.ACTIVE).size();
            return new PublicDetails(p.getId(), p.getType(), p.getTitle(), p.getDescription(), a.getCity(), a.getDistrict(),
                    hide ? null : a.getStreet(), hide ? null : a.getHouseNumber(), a.getComplexName(),
                    geo == null ? null : geo[0], geo == null ? null : geo[1], hide,
                    listing.getPrice(), listing.getCurrency(), p.getAreaSqm(), p.getLivingAreaSqm(), p.getKitchenAreaSqm(),
                    p.getLandAreaSqm(), p.getRooms(), p.getBedrooms(), p.getBathrooms(), p.getFloor(), p.getTotalFloors(),
                    p.getYearBuilt(), p.getCeilingHeightM(), p.getMarket(), p.getWallMaterial(), p.getCondition(),
                    p.getHeating(), p.getLandPurpose(), p.getCommercialType(), p.getHasElevator(), p.getParkingSpaces(),
                    p.getFeatures(), photos, plans, videos, agents, info(tenant, active), listing.getPublishedAt(), similar);
        });
    }

    /** Публічна заявка — потрапляє у вхідні агенції й у таймлайн об'єкта. */
    public Inquiry inquire(String slug, UUID propertyId, InquiryRequest request) {
        List<FieldError> errors = new ArrayList<>();
        if (request.name() == null || request.name().isBlank()) {
            errors.add(new FieldError("name", "required"));
        }
        if ((request.phone() == null || request.phone().isBlank()) && (request.email() == null || request.email().isBlank())) {
            errors.add(new FieldError("phone", "contactRequired"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Заповніть ім'я та телефон або email", errors);
        }
        Tenant tenant = agency(slug);
        return TenantContext.callAs(tenant.getId(), () -> tx.execute(s -> {
            Listing listing = listingService.current(propertyId)
                    .filter(l -> l.getStatus() == Listing.Status.ACTIVE)
                    .orElseThrow(() -> new NotFoundException("Об'єкт не знайдено"));
            UUID agentId = propertyAgentRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId).stream()
                    .filter(pa -> pa.getRole() == PropertyAgent.Role.LEAD).findFirst()
                    .map(PropertyAgent::getAgentId).orElse(listing.getAgentId());
            Inquiry inquiry = inquiryService.create(tenant.getId(), listing.getId(), agentId, request.name().trim(),
                    blank(request.email()) ? null : request.email().trim(), blank(request.phone()) ? null : request.phone().trim(),
                    request.message());
            activity.record(tenant.getId(), SubjectType.PROPERTY, propertyId, "INQUIRY_RECEIVED",
                    Map.of("name", request.name().trim()), null, true);
            return inquiry;
        }));
    }

    // ---------- helpers (під TenantContext) ----------

    private List<PublicCard> activeCards() {
        List<Listing> active = listingRepository.findByStatus(Listing.Status.ACTIVE);
        if (active.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = active.stream().map(Listing::getPropertyId).toList();
        Map<UUID, Property> properties = propertyRepository.findAllById(ids).stream()
                .filter(p -> p.getStatus() != Property.Status.ARCHIVED)
                .collect(Collectors.toMap(Property::getId, Function.identity()));
        Map<UUID, Address> addresses = addressRepository.findAllById(properties.values().stream().map(Property::getAddressId).toList())
                .stream().collect(Collectors.toMap(Address::getId, Function.identity()));
        Map<UUID, String> covers = mediaService.coverThumbs(ids);
        Map<UUID, Long> photoCounts = mediaService.photoCounts(ids);
        Map<UUID, Boolean> reduced = new HashMap<>();
        for (Listing l : active) {
            var history = listingService.priceHistory(l.getId());
            reduced.put(l.getPropertyId(), history.size() > 1 && history.get(0).getPrice().compareTo(history.get(1).getPrice()) < 0);
        }
        List<PublicCard> cards = new ArrayList<>();
        for (Listing l : active) {
            Property p = properties.get(l.getPropertyId());
            if (p == null) {
                continue;
            }
            Address a = addresses.get(p.getAddressId());
            boolean hide = l.isHideExactAddress();
            double[] geo = a == null ? null : coordinates(a, hide);
            cards.add(new PublicCard(p.getId(), p.getType(), p.getTitle(), a == null ? null : a.getCity(),
                    a == null ? null : a.getDistrict(), hide || a == null ? null : a.getStreet(),
                    hide || a == null ? null : a.getHouseNumber(), a == null ? null : a.getComplexName(),
                    l.getPrice(), l.getCurrency(), p.getAreaSqm(), p.getLandAreaSqm(), p.getRooms(), p.getFloor(),
                    p.getTotalFloors(), covers.get(p.getId()), photoCounts.getOrDefault(p.getId(), 0L).intValue(),
                    geo == null ? null : geo[0], geo == null ? null : geo[1], hide, l.getPublishedAt(),
                    reduced.getOrDefault(p.getId(), false)));
        }
        return cards;
    }

    /** Точні координати або округлені до ~300 м, якщо адресу приховано. */
    static double[] coordinates(Address a, boolean approximate) {
        if (a.getGeoLocation() == null) {
            return null;
        }
        double lat = a.getGeoLocation().getY();
        double lon = a.getGeoLocation().getX();
        if (!approximate) {
            return new double[]{lat, lon};
        }
        return new double[]{Math.round(lat / 0.003) * 0.003, Math.round(lon / 0.004) * 0.004};
    }

    private static AgencyInfo info(Tenant t, long active) {
        return new AgencyInfo(t.getSlug(), t.getName(), t.getPublicPhone(), t.getPublicEmail(), t.getWebsite(),
                t.getAbout(), t.getPublicCity(), active);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
