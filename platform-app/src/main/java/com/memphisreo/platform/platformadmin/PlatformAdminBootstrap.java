package com.memphisreo.platform.platformadmin;

import com.memphisreo.security.PlatformStaff;
import com.memphisreo.security.PlatformStaffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Створює першого супер-адміна платформи з конфігурації, якщо його ще немає.
 * Пароль — лише з середовища (локально його генерує dev.sh у .env.local),
 * ніколи не з репозиторію. Наявний обліковий запис не змінюється: зміна
 * змінних середовища не перезаписує пароль.
 */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final PlatformStaffRepository platformStaffRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public PlatformAdminBootstrap(PlatformStaffRepository platformStaffRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${memphisreo.bootstrap-admin.email:}") String email,
                                  @Value("${memphisreo.bootstrap-admin.password:}") String password) {
        this.platformStaffRepository = platformStaffRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase();
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) {
            return;
        }
        if (platformStaffRepository.findByEmail(email).isPresent()) {
            return;
        }
        PlatformStaff staff = new PlatformStaff();
        staff.setEmail(email);
        staff.setPasswordHash(passwordEncoder.encode(password));
        platformStaffRepository.save(staff);
        log.info("Створено супер-адміна платформи {}", email);
    }
}
