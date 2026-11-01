package com.civix.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.civix.auth.domain.User;
import com.civix.config.CivixProperties;

/** Issues HS256 access tokens ({@code sub} = user id, {@code roles} = role names). */
@Service
public class JwtService {

    public static final String ISSUER = "civix-api";

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, CivixProperties props, Clock clock) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
        this.clock = clock;
    }

    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        List<String> roles = user.roles().stream().map(Enum::name).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    public Duration ttl() {
        return ttl;
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedAccessToken[expiresAt=" + expiresAt + "]";
        }
    }
}
