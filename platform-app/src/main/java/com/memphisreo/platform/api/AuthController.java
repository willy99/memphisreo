package com.memphisreo.platform.api;

import com.memphisreo.platform.agent.AcceptInviteRequest;
import com.memphisreo.platform.agent.AgentInvitationService;
import com.memphisreo.platform.auth.PasswordResetService;
import com.memphisreo.security.LoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String email, String password) {
    }

    public record PasswordResetRequest(String email) {
    }

    public record PasswordResetConfirmRequest(String token, String newPassword) {
    }

    private final LoginService loginService;
    private final AgentInvitationService agentInvitationService;
    private final PasswordResetService passwordResetService;

    public AuthController(LoginService loginService, AgentInvitationService agentInvitationService,
                          PasswordResetService passwordResetService) {
        this.loginService = loginService;
        this.agentInvitationService = agentInvitationService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginService.LoginResult> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(loginService.login(request.email(), request.password()));
    }

    /** Публічний — permitAll на /api/auth/** вже покриває, docs/security.md §9. */
    @PostMapping("/accept-invite")
    public ResponseEntity<Void> acceptInvite(@RequestBody AcceptInviteRequest request) {
        agentInvitationService.acceptInvite(request.token(), request.password());
        return ResponseEntity.ok().build();
    }

    /** Завжди 202 — однакова відповідь для існуючого й неіснуючого email (docs/security.md §10). */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
