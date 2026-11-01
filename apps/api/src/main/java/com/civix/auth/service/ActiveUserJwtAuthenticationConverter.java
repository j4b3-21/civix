package com.civix.auth.service;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.civix.auth.domain.UserStatus;
import com.civix.auth.repository.UserRepository;

/**
 * Maps a validated access token to an authentication, rejecting tokens whose account is no longer active
 * (deleted or administratively locked) so that account deletion takes effect immediately.
 */
public class ActiveUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public ActiveUserJwtAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Token subject is invalid");
        }
        UserStatus status = users.findStatusById(userId).orElse(null);
        if (status != UserStatus.ACTIVE) {
            throw new InvalidBearerTokenException("Account is not active");
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        List<GrantedAuthority> authorities = roles == null ? List.of()
                : roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return new JwtAuthenticationToken(jwt, authorities, userId.toString());
    }
}
