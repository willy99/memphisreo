package com.memphisreo.platform.auth;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.mail.EmailSender;
import com.memphisreo.security.AccountIdentity;
import com.memphisreo.security.AccountIdentityRepository;
import com.memphisreo.security.PasswordResetToken;
import com.memphisreo.security.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private final AccountIdentityRepository accounts = mock(AccountIdentityRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private PasswordResetService service;
    private AccountIdentity account;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(accounts, tokens, encoder, emailSender, "http://web",
                Clock.fixed(NOW, ZoneOffset.UTC));
        account = new AccountIdentity();
        account.setId(UUID.randomUUID());
        account.setEmail("agent@example.com");
        account.setStatus(AccountIdentity.Status.ACTIVE);
        when(encoder.encode(any())).thenReturn("hashed");
    }

    @Test
    void request_storesOnlyHashOfToken_andEmailsTheRawToken() {
        when(accounts.findByEmail("agent@example.com")).thenReturn(Optional.of(account));
        when(tokens.findByAccountIdAndUsedAtIsNull(account.getId())).thenReturn(List.of());

        service.requestReset("agent@example.com");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        ArgumentCaptor<EmailSender.Email> mail = ArgumentCaptor.forClass(EmailSender.Email.class);
        verify(emailSender).send(mail.capture());

        String rawToken = mail.getValue().textBody().replaceAll("(?s).*token=([A-Za-z0-9_-]+).*", "$1");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetService.sha256(rawToken));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(PasswordResetService.TOKEN_TTL));
    }

    @Test
    void request_forDisabledAccount_sendsNothing() {
        account.setStatus(AccountIdentity.Status.DISABLED);
        when(accounts.findByEmail("agent@example.com")).thenReturn(Optional.of(account));

        service.requestReset("agent@example.com");

        verifyNoInteractions(emailSender);
        verify(tokens, never()).save(any());
    }

    @Test
    void request_marksPreviousUnusedTokensUsed() {
        PasswordResetToken previous = token(NOW.plusSeconds(600));
        when(accounts.findByEmail("agent@example.com")).thenReturn(Optional.of(account));
        when(tokens.findByAccountIdAndUsedAtIsNull(account.getId())).thenReturn(List.of(previous));

        service.requestReset("agent@example.com");

        assertThat(previous.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void reset_withExpiredToken_isRejected() {
        when(tokens.findByTokenHash(PasswordResetService.sha256("raw"))).thenReturn(Optional.of(token(NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.resetPassword("raw", "NewPassword123!"))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(encoder);
    }

    @Test
    void reset_setsPassword_bumpsTokenVersion_andConsumesToken() {
        PasswordResetToken resetToken = token(NOW.plusSeconds(60));
        when(tokens.findByTokenHash(PasswordResetService.sha256("raw"))).thenReturn(Optional.of(resetToken));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        service.resetPassword("raw", "NewPassword123!");

        assertThat(account.getPasswordHash()).isEqualTo("hashed");
        assertThat(account.getTokenVersion()).isEqualTo(1);
        assertThat(resetToken.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void reset_withShortPassword_isRejectedBeforeTokenLookup() {
        assertThatThrownBy(() -> service.resetPassword("raw", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tokens);
    }

    private PasswordResetToken token(Instant expiresAt) {
        PasswordResetToken t = new PasswordResetToken();
        t.setAccountId(account.getId());
        t.setExpiresAt(expiresAt);
        return t;
    }
}
