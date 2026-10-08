package com.memphisreo.platform.sale;

import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.common.TenantContext;
import com.memphisreo.listing.ListingService;
import com.memphisreo.listing.ListingService.Change;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.HashMap;

/**
 * Щоденно: ACTIVE-лістинги з протермінованим мандатом → EXPIRED, по кожній
 * агенції окремо (RLS). При тисячах агенцій — перевести на запит по всій
 * комірці роллю з BYPASSRLS (ADR-001).
 */
@Component
public class SaleMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(SaleMaintenanceJob.class);

    private final TenantRepository tenantRepository;
    private final ListingService listingService;
    private final ActivityRecorder activity;
    private final TransactionTemplate tx;

    public SaleMaintenanceJob(TenantRepository tenantRepository, ListingService listingService,
                              ActivityRecorder activity, TransactionTemplate tx) {
        this.tenantRepository = tenantRepository;
        this.listingService = listingService;
        this.activity = activity;
        this.tx = tx;
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void expireMandates() {
        LocalDate today = LocalDate.now();
        for (Tenant tenant : tenantRepository.findAll()) {
            TenantContext.runAs(tenant.getId(), () -> tx.executeWithoutResult(s -> {
                for (Change change : listingService.expireMandates(today)) {
                    activity.record(tenant.getId(), SubjectType.PROPERTY, change.listing().getPropertyId(),
                            change.event(), new HashMap<>(change.details()), null, true);
                    log.info("Мандат протерміновано: tenant {}, property {}", tenant.getId(), change.listing().getPropertyId());
                }
            }));
        }
    }
}
