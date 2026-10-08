package com.memphisreo.platform.owner;

import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientRepository;
import com.memphisreo.crm.OwnerAccessToken;
import com.memphisreo.crm.OwnerAccessTokenRepository;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingService;
import com.memphisreo.media.MediaService;
import com.memphisreo.platform.sale.SaleDtos.OwnerLink;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import com.memphisreo.platform.sale.SaleService;
import com.memphisreo.property.Address;
import com.memphisreo.property.AddressRepository;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Кабінет власника: посилання без пароля на звіт по СВОЇХ об'єктах
 * (лістинги, де seller_client_id = цей контакт). Власник бачить лише
 * owner_visible події. Токен — 256 біт, у БД лише SHA-256, живе 90 днів.
 */
@Service
public class OwnerPortalService {

    static final Duration TOKEN_TTL = Duration.ofDays(90);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record OwnerProperty(UUID id, String title, Property.Type type, String city, String district, String street,
                                String houseNumber, Listing.Status status, BigDecimal price, String currency,
                                String coverUrl, Instant publishedAt, List<TimelineEntry> timeline) {
    }

    public record OwnerReport(String agencyName, String agencyPhone, String ownerFirstName, List<OwnerProperty> properties) {
    }

    private final OwnerAccessTokenRepository tokenRepository;
    private final ClientRepository clientRepository;
    private final ListingService listingService;
    private final PropertyRepository propertyRepository;
    private final AddressRepository addressRepository;
    private final MediaService mediaService;
    private final SaleService saleService;
    private final ActivityRecorder activity;
    private final TenantRepository tenantRepository;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String webBaseUrl;

    public OwnerPortalService(OwnerAccessTokenRepository tokenRepository, ClientRepository clientRepository,
                              ListingService listingService, PropertyRepository propertyRepository,
                              AddressRepository addressRepository, MediaService mediaService, SaleService saleService,
                              ActivityRecorder activity, TenantRepository tenantRepository, TransactionTemplate tx,
                              Clock clock, @Value("${memphisreo.web-base-url}") String webBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.clientRepository = clientRepository;
        this.listingService = listingService;
        this.propertyRepository = propertyRepository;
        this.addressRepository = addressRepository;
        this.mediaService = mediaService;
        this.saleService = saleService;
        this.activity = activity;
        this.tenantRepository = tenantRepository;
        this.tx = tx;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl;
    }

    /** Агент створює посилання для власника (попередні лишаються дійсними до закінчення строку). */
    @Transactional
    public OwnerLink issueLink(UUID tenantId, UUID agentId, UUID clientId) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new NotFoundException("Контакт не знайдено"));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OwnerAccessToken access = new OwnerAccessToken();
        access.setTenantId(tenantId);
        access.setClientId(client.getId());
        access.setTokenHash(sha256(token));
        access.setExpiresAt(clock.instant().plus(TOKEN_TTL));
        access.setCreatedByAgentId(agentId);
        tokenRepository.save(access);
        activity.record(tenantId, SubjectType.CLIENT, clientId, "OWNER_LINK_ISSUED", java.util.Map.of(), agentId, false);
        return new OwnerLink(webBaseUrl + "/owner/" + token, access.getExpiresAt());
    }

    /**
     * Публічний вхід за токеном: tenant невідомий до перевірки, тому токен
     * шукається по всіх агенціях (control plane знає їх список); хеш унікальний.
     */
    public OwnerReport report(String token) {
        String hash = sha256(token == null ? "" : token.trim());
        for (Tenant tenant : tenantRepository.findAll()) {
            OwnerReport report = TenantContext.callAs(tenant.getId(), () -> tokenRepository.findByTokenHash(hash)
                    .map(access -> buildReport(tenant, access)).orElse(null));
            if (report != null) {
                return report;
            }
        }
        throw new ForbiddenException("Посилання недійсне або протерміноване");
    }

    private OwnerReport buildReport(Tenant tenant, OwnerAccessToken access) {
        if (access.getExpiresAt().isBefore(clock.instant())) {
            throw new ForbiddenException("Посилання недійсне або протерміноване");
        }
        tx.executeWithoutResult(s -> {
            access.setLastUsedAt(clock.instant());
            tokenRepository.save(access);
        });
        Client owner = clientRepository.findById(access.getClientId()).orElseThrow(() -> new ForbiddenException("Контакт не знайдено"));
        List<OwnerProperty> properties = listingService.activeForSeller(owner.getId()).stream().map(listing -> {
            Property p = propertyRepository.findById(listing.getPropertyId()).orElse(null);
            if (p == null) {
                return null;
            }
            Address a = addressRepository.findById(p.getAddressId()).orElse(null);
            String cover = mediaService.coverThumbs(List.of(p.getId())).get(p.getId());
            List<TimelineEntry> timeline = saleService.timeline(p.getId(), true);
            return new OwnerProperty(p.getId(), p.getTitle(), p.getType(), a == null ? null : a.getCity(),
                    a == null ? null : a.getDistrict(), a == null ? null : a.getStreet(), a == null ? null : a.getHouseNumber(),
                    listing.getStatus(), listing.getPrice(), listing.getCurrency(), cover, listing.getPublishedAt(), timeline);
        }).filter(java.util.Objects::nonNull).toList();
        return new OwnerReport(tenant.getName(), tenant.getPublicPhone(), owner.getFirstName(), properties);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
