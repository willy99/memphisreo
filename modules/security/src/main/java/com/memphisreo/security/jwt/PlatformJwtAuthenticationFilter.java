package com.memphisreo.security.jwt;

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
 * Аналог {@link TenantJwtAuthenticationFilter}, але для platform-admin realm:
 * окремий підписний ключ, окрема ідентичність (PlatformStaff), без TenantContext —
 * платформний адмін не належить жодному tenant-у. docs/security.md §6.
 */
public class PlatformJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenRevocationCheck revocationCheck;

    public PlatformJwtAuthenticationFilter(JwtService jwtService, TokenRevocationCheck revocationCheck) {
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
                UUID staffId = UUID.fromString(claims.getSubject());
                int tokenVersion = claims.get("token_version", Integer.class);

                if (revocationCheck.isStillValid(staffId, tokenVersion)) {
                    List<SimpleGrantedAuthority> authorities = JwtService.permissions(claims).stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    Authentication auth = new UsernamePasswordAuthenticationToken(staffId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
