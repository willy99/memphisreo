package com.memphisreo.platform.api;

import com.memphisreo.platform.agent.AcceptInviteRequest;
import com.memphisreo.platform.agent.AgentInvitationService;
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

    private final LoginService loginService;
    private final AgentInvitationService agentInvitationService;

    public AuthController(LoginService loginService, AgentInvitationService agentInvitationService) {
        this.loginService = loginService;
        this.agentInvitationService = agentInvitationService;
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
}
