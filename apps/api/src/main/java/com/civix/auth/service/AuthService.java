package com.civix.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.domain.AuditOutcome;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.auth.domain.Role;
import com.civix.auth.domain.User;
import com.civix.auth.domain.UserStatus;
import com.civix.auth.dto.LoginRequest;
import com.civix.auth.dto.RegisterRequest;
import com.civix.auth.dto.UserDto;
import com.civix.auth.repository.UserRepository;
import com.civix.citizen.domain.CitizenProfile;
import com.civix.citizen.repository.CitizenProfileRepository;
import com.civix.common.api.ApiException;
import com.civix.common.api.StateChangingApiException;
import com.civix.config.CivixProperties;

@Service
public class AuthService {

    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final CitizenProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final Clock clock;
    private final Set<String> adminEmails;
    private final String timingDummyHash;

    public AuthService(UserRepository users, CitizenProfileRepository profiles, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens, AuditService audit, Clock clock,
                       CivixProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
        this.adminEmails = props.adminEmailSet();
        // Used to spend comparable time on unknown e-mail addresses (no user enumeration via timing).
        this.timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResult register(RegisterRequest request, String userAgent) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailNormalized(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this e-mail address already exists");
        }
        Instant now = clock.instant();
        Role role = adminEmails.contains(email) ? Role.ADMIN : Role.CITIZEN;
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(), role, now));
        profiles.save(new CitizenProfile(user.getId(), now));
        audit.record(AuditEntry.of(AuditCategory.AUTH, "REGISTER")
                .user(user.getId()).actor(ActorType.USER, user.getId().toString())
                .resource("USER", user.getId().toString())
                .summary("Account created" + (role == Role.ADMIN ? " with administrator role" : "")));
        return issueTokens(user, userAgent);
    }

    @Transactional(noRollbackFor = StateChangingApiException.class)
    public AuthResult login(LoginRequest request, String userAgent) {
        String email = normalizeEmail(request.email());
        Instant now = clock.instant();
        Optional<User> found = users.findByEmailForUpdate(email);
        if (found.isEmpty() || found.get().getStatus() == UserStatus.DELETED) {
            passwordEncoder.matches(request.password(), timingDummyHash);
            audit.record(AuditEntry.of(AuditCategory.AUTH, "LOGIN_FAILED").actor(ActorType.USER, null)
                    .outcome(AuditOutcome.FAILURE).summary("Sign-in failed: unknown account"));
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.getStatus() == UserStatus.LOCKED || user.isLocked(now)) {
            audit.record(AuditEntry.of(AuditCategory.AUTH, "LOGIN_BLOCKED").user(user.getId())
                    .actor(ActorType.USER, user.getId().toString()).outcome(AuditOutcome.DENIED)
                    .summary("Sign-in blocked: account temporarily locked"));
            throw locked();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean lockedNow = user.recordFailedLogin(now, MAX_FAILED_LOGINS, LOCK_DURATION);
            audit.record(AuditEntry.of(AuditCategory.AUTH, lockedNow ? "ACCOUNT_LOCKED" : "LOGIN_FAILED")
                    .user(user.getId()).actor(ActorType.USER, user.getId().toString()).outcome(AuditOutcome.FAILURE)
                    .summary(lockedNow ? "Account locked for 15 minutes after repeated failed sign-ins"
                            : "Sign-in failed: wrong password"));
            throw lockedNow ? locked() : invalidCredentials();
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            // Legacy or weaker hash (e.g. bcrypt): transparently re-hash with the current default encoder.
            user.replacePasswordHash(passwordEncoder.encode(request.password()));
        }
        user.recordSuccessfulLogin(now);
        audit.record(AuditEntry.of(AuditCategory.AUTH, "LOGIN").user(user.getId())
                .actor(ActorType.USER, user.getId().toString()).summary("Signed in"));
        return issueTokens(user, userAgent);
    }

    @Transactional(noRollbackFor = StateChangingApiException.class)
    public AuthResult refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "No session to refresh");
        }
        RefreshTokenService.Rotation rotation;
        try {
            rotation = refreshTokens.rotate(rawRefreshToken, userAgent);
        } catch (StateChangingApiException e) {
            if ("REFRESH_TOKEN_REUSED".equals(e.code())) {
                audit.record(AuditEntry.of(AuditCategory.AUTH, "REFRESH_TOKEN_REUSE").actor(ActorType.SYSTEM, null)
                        .outcome(AuditOutcome.DENIED)
                        .summary("A previously used session token was presented again; all sessions of that sign-in were revoked"));
            }
            throw e;
        }
        User user = users.findById(rotation.userId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> {
                    refreshTokens.revokeAll(rotation.userId());
                    return new StateChangingApiException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                            "Your session is no longer valid. Please sign in again.");
                });
        JwtService.IssuedAccessToken access = jwtService.issue(user);
        return new AuthResult(UserDto.from(user), access.value(), access.expiresAt(),
                rotation.token().rawToken(), rotation.token().expiresAt());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revokeFamilyOf(rawRefreshToken).ifPresent(userId ->
                audit.record(AuditEntry.of(AuditCategory.AUTH, "LOGOUT").user(userId)
                        .actor(ActorType.USER, userId.toString()).summary("Signed out")));
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        return users.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(UserDto::from)
                .orElseThrow(() -> new ApiException("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "Authentication is required"));
    }

    private AuthResult issueTokens(User user, String userAgent) {
        JwtService.IssuedAccessToken access = jwtService.issue(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issueNewFamily(user.getId(), userAgent);
        return new AuthResult(UserDto.from(user), access.value(), access.expiresAt(), refresh.rawToken(), refresh.expiresAt());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static StateChangingApiException invalidCredentials() {
        return new StateChangingApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "E-mail or password is incorrect");
    }

    private static StateChangingApiException locked() {
        return new StateChangingApiException("ACCOUNT_LOCKED", HttpStatus.LOCKED,
                "Too many failed sign-in attempts. Try again in 15 minutes.");
    }
}
