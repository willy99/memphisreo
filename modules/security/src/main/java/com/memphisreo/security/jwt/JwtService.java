package com.memphisreo.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Підпис/валідація JWT одним HMAC-ключем. Tenant API і platform-admin
 * використовують ОКРЕМІ інстанси цього сервісу з різними ключами —
 * витік одного не дає підробити токен іншого realm-у (docs/security.md §6).
 */
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(String base64Secret) {
        this.signingKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(base64Secret));
    }

    public String issueAccessToken(Map<String, Object> claims, String subject, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public static List<String> permissions(Claims claims) {
        Object raw = claims.get("permissions");
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }
}
