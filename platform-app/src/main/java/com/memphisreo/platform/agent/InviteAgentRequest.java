package com.memphisreo.platform.agent;

import java.util.UUID;

public record InviteAgentRequest(String email, String firstName, String lastName, UUID roleId) {
}
