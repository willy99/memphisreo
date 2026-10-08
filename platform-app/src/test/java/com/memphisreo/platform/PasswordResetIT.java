package com.memphisreo.platform;

import com.memphisreo.common.mail.EmailSender;
import com.memphisreo.platform.api.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Скидання пароля поштою — docs/security.md §10. */
class PasswordResetIT extends AbstractIntegrationTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

    @Test
    void resetViaEmailedToken_changesPassword_revokesOldSessions_andTokenIsSingleUse() {
        String slug = "reset-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin@" + slug + ".ua";
        register(slug, email, "OldPassword123!", "UA");
        String oldSessionToken = login(email, "OldPassword123!");

        assertThat(requestReset(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        List<EmailSender.Email> mails = emailSender.sentTo(email);
        assertThat(mails).hasSize(1);
        String token = extractToken(mails.get(0));

        assertThat(confirm(token, "NewPassword123!").getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(loginStatus(email, "OldPassword123!")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(loginStatus(email, "NewPassword123!")).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange("/api/dashboard/stats", HttpMethod.GET, authed(oldSessionToken),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(confirm(token, "AnotherPassword123!").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void newRequestInvalidatesPreviousToken() {
        String slug = "reset2-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin@" + slug + ".ua";
        register(slug, email, "OldPassword123!", "UA");

        requestReset(email);
        requestReset(email);
        List<EmailSender.Email> mails = emailSender.sentTo(email);
        assertThat(mails).hasSize(2);

        assertThat(confirm(extractToken(mails.get(0)), "NewPassword123!").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(confirm(extractToken(mails.get(1)), "NewPassword123!").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownEmail_getsSameResponse_andNoEmail() {
        String email = "nobody-" + UUID.randomUUID() + "@example.com";
        assertThat(requestReset(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(emailSender.sentTo(email)).isEmpty();
    }

    @Test
    void invalidTokenOrShortPassword_isRejected() {
        assertThat(confirm("not-a-real-token", "NewPassword123!").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(confirm("not-a-real-token", "short").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Void> requestReset(String email) {
        return restTemplate.postForEntity("/api/auth/password-reset/request",
                new AuthController.PasswordResetRequest(email), Void.class);
    }

    private ResponseEntity<String> confirm(String token, String newPassword) {
        return restTemplate.postForEntity("/api/auth/password-reset/confirm",
                new AuthController.PasswordResetConfirmRequest(token, newPassword), String.class);
    }

    private HttpStatus loginStatus(String email, String password) {
        return HttpStatus.valueOf(restTemplate.postForEntity("/api/auth/login",
                new AuthController.LoginRequest(email, password), String.class).getStatusCode().value());
    }

    private static String extractToken(EmailSender.Email email) {
        Matcher matcher = TOKEN_IN_LINK.matcher(email.textBody());
        assertThat(matcher.find()).as("посилання з токеном у листі").isTrue();
        return matcher.group(1);
    }
}
