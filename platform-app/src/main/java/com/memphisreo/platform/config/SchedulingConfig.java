package com.memphisreo.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Планові задачі: протермінування мандатів (SaleMaintenanceJob) тощо. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
