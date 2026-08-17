package com.memphisreo.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Модулі живуть у сусідніх пакетах com.memphisreo.<module>, не під
 * com.memphisreo.platform — тому явний базовий пакет для сканування
 * (docs/architecture.md §1, §2).
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.memphisreo")
@EntityScan(basePackages = "com.memphisreo")
@EnableJpaRepositories(basePackages = "com.memphisreo")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
