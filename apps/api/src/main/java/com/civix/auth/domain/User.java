package com.civix.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, Role role, Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.failedLoginCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Records a failed login; returns {@code true} if this failure locked the account. */
    public boolean recordFailedLogin(Instant now, int maxFailures, Duration lockDuration) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            // previous lock expired: start counting afresh
            lockedUntil = null;
            failedLoginCount = 0;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void replacePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Pseudonymises the account. The row is kept so that audit events stay attributable to a pseudonymous id. */
    public void markDeleted(String unusablePasswordHash) {
        this.status = UserStatus.DELETED;
        this.email = "deleted+" + id + "@invalid";
        this.fullName = "Deleted user";
        this.passwordHash = unusablePasswordHash;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public List<Role> roles() {
        return role == Role.ADMIN ? List.of(Role.CITIZEN, Role.ADMIN) : List.of(Role.CITIZEN);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
