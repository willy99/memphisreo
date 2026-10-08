package com.memphisreo.platform.auth;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.mail.EmailSender;
import com.memphisreo.security.AccountIdentity;
import com.memphisreo.security.AccountIdentityRepository;
import com.memphisreo.security.PasswordResetToken;
import com.memphisreo.security.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Скидання пароля агента поштою — docs/security.md §10.
 *
 * - Відповідь на запит однакова, чи існує email, чи ні (без перебору акаунтів).
 * - Токен — 256 біт випадковості, у листі; у БД — лише його SHA-256.
 * - Одноразовий, живе {@link #TOKEN_TTL}; новий запит анулює попередні.
 * - Успішне скидання інкрементує token_version — усі видані JWT стають недійсними.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountIdentityRepository accountIdentityRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final String webBaseUrl;
    private final Clock clock;

    public PasswordResetService(AccountIdentityRepository accountIdentityRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailSender emailSender,
                                @Value("${memphisreo.web-base-url}") String webBaseUrl,
                                Clock clock) {
        this.accountIdentityRepository = accountIdentityRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.webBaseUrl = webBaseUrl;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        accountIdentityRepository.findByEmail(email.trim())
                .filter(account -> account.getStatus() == AccountIdentity.Status.ACTIVE)
                .ifPresentOrElse(this::issueToken,
                        () -> log.info("Запит на скидання пароля для невідомого/неактивного email — лист не надсилається"));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Пароль — щонайменше " + MIN_PASSWORD_LENGTH + " символів");
        }
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(sha256(token == null ? "" : token.trim()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ForbiddenException("Посилання для скидання пароля недійсне або протерміноване"));
        AccountIdentity account = accountIdentityRepository.findById(resetToken.getAccountId())
                .filter(a -> a.getStatus() == AccountIdentity.Status.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Посилання для скидання пароля недійсне або протерміноване"));

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setTokenVersion(account.getTokenVersion() + 1);
        resetToken.setUsedAt(now);
        log.info("Пароль облікового запису {} скинуто", account.getId());
    }

    private void issueToken(AccountIdentity account) {
        Instant now = clock.instant();
        tokenRepository.findByAccountIdAndUsedAtIsNull(account.getId()).forEach(t -> t.setUsedAt(now));

        String token = newToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setAccountId(account.getId());
        resetToken.setTokenHash(sha256(token));
        resetToken.setExpiresAt(now.plus(TOKEN_TTL));
        tokenRepository.save(resetToken);

        emailSender.send(resetEmail(account.getEmail(), token));
    }

    private EmailSender.Email resetEmail(String to, String token) {
        String link = webBaseUrl + "/reset-password?token=" + token;
        long minutes = TOKEN_TTL.toMinutes();
        String text = """
                Вітаємо!

                Ми отримали запит на скидання пароля до Memphis. Щоб задати новий пароль, перейдіть за посиланням:
                %s

                Або введіть цей код на сторінці скидання пароля:
                %s

                Посилання й код дійсні %d хвилин і спрацюють лише один раз.
                Якщо ви не надсилали запит — просто проігноруйте цей лист, ваш пароль не зміниться.
                """.formatted(link, token, minutes);
        String html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:520px;margin:0 auto;color:#1c1b18">
                  <h2 style="margin:0 0 16px">Скидання пароля</h2>
                  <p>Ми отримали запит на скидання пароля до <b>Memphis</b>.</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#0f6b5c;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:600">Задати новий пароль</a>
                  </p>
                  <p>Або введіть цей код на сторінці скидання пароля:</p>
                  <p style="font-family:ui-monospace,Consolas,monospace;font-size:14px;background:#f1f0ec;padding:10px 12px;border-radius:6px;word-break:break-all">%s</p>
                  <p style="color:#86847c;font-size:13px">Посилання й код дійсні %d хвилин і спрацюють лише один раз. Якщо ви не надсилали запит — проігноруйте цей лист, пароль не зміниться.</p>
                </div>
                """.formatted(link, token, minutes);
        return new EmailSender.Email(to, "Скидання пароля — Memphis", text, html);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступний", e);
        }
    }
}
