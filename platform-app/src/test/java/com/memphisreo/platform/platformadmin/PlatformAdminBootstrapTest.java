package com.memphisreo.platform.platformadmin;

import com.memphisreo.security.PlatformStaff;
import com.memphisreo.security.PlatformStaffRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformAdminBootstrapTest {

    private final PlatformStaffRepository repository = mock(PlatformStaffRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsAdmin_withNormalizedEmailAndHashedPassword() {
        when(repository.findByEmail("willy@example.com")).thenReturn(Optional.empty());
        when(encoder.encode("secret-password")).thenReturn("hashed");

        new PlatformAdminBootstrap(repository, encoder, " Willy@Example.com ", "secret-password").run(null);

        ArgumentCaptor<PlatformStaff> saved = ArgumentCaptor.forClass(PlatformStaff.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("willy@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void existingAdmin_isNotOverwritten() {
        when(repository.findByEmail("willy@example.com")).thenReturn(Optional.of(new PlatformStaff()));

        new PlatformAdminBootstrap(repository, encoder, "willy@example.com", "new-password").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void withoutConfiguration_doesNothing() {
        new PlatformAdminBootstrap(repository, encoder, "", "").run(null);

        verifyNoInteractions(repository);
    }
}
