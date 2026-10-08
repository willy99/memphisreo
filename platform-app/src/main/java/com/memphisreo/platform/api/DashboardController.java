package com.memphisreo.platform.api;

import com.memphisreo.platform.dashboard.TenantStats;
import com.memphisreo.platform.dashboard.TenantStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Головна сторінка агенції. Будь-який автентифікований агент бачить загальні цифри своєї агенції. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TenantStatsService tenantStatsService;

    public DashboardController(TenantStatsService tenantStatsService) {
        this.tenantStatsService = tenantStatsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<TenantStats> stats() {
        return ResponseEntity.ok(tenantStatsService.currentTenantStats());
    }
}
