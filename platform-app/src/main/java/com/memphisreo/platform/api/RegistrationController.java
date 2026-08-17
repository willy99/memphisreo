package com.memphisreo.platform.api;

import com.memphisreo.platform.registration.RegisterTenantRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.platform.registration.TenantRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/public/** — permitAll, docs/security.md §7. */
@RestController
@RequestMapping("/api/public/tenants")
public class RegistrationController {

    private final TenantRegistrationService registrationService;

    public RegistrationController(TenantRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterTenantResponse> register(@RequestBody RegisterTenantRequest request) {
        return ResponseEntity.ok(registrationService.register(request));
    }
}
