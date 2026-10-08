package com.memphisreo.security;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.security.jwt.JwtService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Логін співробітника платформи. Свідомо окремий від {@link LoginService}:
 * інша таблиця ({@link PlatformStaff}), інший ключ підпису JWT, інший
 * SecurityFilterChain — docs/security.md §6.
 */
@Service
public class PlatformLoginService {

    /** Єдиний платформний дозвіл поки що — повний доступ супер-адміна. */
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /**
     * Тимчасово довгий TTL: refresh-токенів ще немає (docs/security.md §4),
     * а миттєве відкликання вже працює через token_version.
     */
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(8);

    private final PlatformStaffRepository platformStaffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService platformJwtService;

    public PlatformLoginService(PlatformStaffRepository platformStaffRepository,
                                PasswordEncoder passwordEncoder,
                                @Qualifier("platformJwtService") JwtService platformJwtService) {
        this.platformStaffRepository = platformStaffRepository;
        this.passwordEncoder = passwordEncoder;
        this.platformJwtService = platformJwtService;
    }

    public LoginService.LoginResult login(String email, String rawPassword) {
        PlatformStaff staff = platformStaffRepository.findByEmail(normalize(email))
                .orElseThrow(() -> new ForbiddenException("Невірний email або пароль"));
        if (staff.getStatus() != PlatformStaff.Status.ACTIVE
                || !passwordEncoder.matches(rawPassword, staff.getPasswordHash())) {
            throw new ForbiddenException("Невірний email або пароль");
        }
        Map<String, Object> claims = Map.of(
                "token_version", staff.getTokenVersion(),
                "email", staff.getEmail(),
                "permissions", List.of(PLATFORM_ADMIN));
        String token = platformJwtService.issueAccessToken(claims, staff.getId().toString(), ACCESS_TOKEN_TTL);
        return new LoginService.LoginResult(token, ACCESS_TOKEN_TTL);
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
