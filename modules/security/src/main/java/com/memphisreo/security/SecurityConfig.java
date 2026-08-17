package com.memphisreo.security;

import com.memphisreo.security.jwt.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Два незалежні SecurityFilterChain — tenant API і platform-admin, кожен зі
 * своїм JWT-фільтром і підписним ключем (docs/security.md §6, §7). Stateless:
 * SessionCreationPolicy.STATELESS узгоджено з горизонтальним масштабуванням
 * (docs/architecture.md §4).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // OWASP-рекомендація за замовчуванням для нових систем — docs/security.md §7
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public JwtService tenantJwtService(@Value("${memphisreo.security.tenant-jwt-secret}") String secret) {
        return new JwtService(secret);
    }

    @Bean
    public JwtService platformJwtService(@Value("${memphisreo.security.platform-jwt-secret}") String secret) {
        return new JwtService(secret);
    }

    @Bean
    public TokenRevocationCheck tenantTokenRevocationCheck(AccountIdentityRepository repository) {
        return new AccountIdentityTokenRevocationCheck(repository);
    }

    @Bean
    public TokenRevocationCheck platformTokenRevocationCheck(PlatformStaffRepository repository) {
        return new PlatformStaffTokenRevocationCheck(repository);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain platformAdminFilterChain(
            HttpSecurity http,
            @Qualifier("platformJwtService") JwtService platformJwtService,
            @Qualifier("platformTokenRevocationCheck") TokenRevocationCheck platformTokenRevocationCheck) throws Exception {

        var filter = new PlatformJwtAuthenticationFilter(platformJwtService, platformTokenRevocationCheck);

        http.securityMatcher("/platform-admin/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/platform-admin/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain tenantApiFilterChain(
            HttpSecurity http,
            @Qualifier("tenantJwtService") JwtService tenantJwtService,
            @Qualifier("tenantTokenRevocationCheck") TokenRevocationCheck tenantTokenRevocationCheck,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        var filter = new TenantJwtAuthenticationFilter(tenantJwtService, tenantTokenRevocationCheck);

        http.securityMatcher("/api/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/public/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Dev-фронтенд (Vite) на іншому origin — тільки /api/**, platform-admin CORS не потребує. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${memphisreo.security.cors-allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
