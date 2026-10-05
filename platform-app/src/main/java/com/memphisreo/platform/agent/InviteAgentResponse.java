package com.memphisreo.platform.agent;

import java.time.Instant;
import java.util.UUID;

/** inviteToken — фронтенд будує посилання сам, листа не надсилаємо (docs/security.md §9). */
public record InviteAgentResponse(UUID agentId, String email, String inviteToken, Instant expiresAt) {
}
