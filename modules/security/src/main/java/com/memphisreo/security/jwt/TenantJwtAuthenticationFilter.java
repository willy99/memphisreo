package com.memphisreo.security.jwt;

import com.memphisreo.common.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Валідує JWT tenant-користувача, заповнює SecurityContext (authorities =
 * permissions з токена) і {@link TenantContext} з claim'у tenant_id — НІКОЛИ
 * з query/path параметра (docs/security.md §7, §8).
 */
public class TenantJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenRevocationCheck revocationCheck;

    public TenantJwtAuthenticationFilter(JwtService jwtService, TokenRevocationCheck revocationCheck) {
        this.jwtService = jwtService;
        this.revocationCheck = revocationCheck;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseAndValidate(header.substring(7));
                UUID accountIdentityId = UUID.fromString(claims.getSubject());
                int tokenVersion = claims.get("token_version", Integer.class);

                if (revocationCheck.isStillValid(accountIdentityId, tokenVersion)) {
                    List<SimpleGrantedAuthority> authorities = JwtService.permissions(claims).stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UUID agentId = UUID.fromString(claims.get("agent_id", String.class));
                    UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
                    AuthenticatedAgent principal = new AuthenticatedAgent(accountIdentityId, agentId, tenantId);

                    Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    String schemaName = claims.get("schema_name", String.class);
                    TenantContext.set(new TenantContext.TenantInfo(tenantId.toString(), schemaName));
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
