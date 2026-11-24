package com.returnmate.purchase.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name="users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name="uk_user_email",
                        columnNames = "email"
                )
        }
)
public class User {
    @Id
    private UUID id;

    @Column(
            name="email",
            nullable = false,
            length = 320
    )
    private String email;

    /** Null for accounts that only sign in with Google. */
    @Column(
            name="password_hash",
            length = 255
    )
    private String passwordHash;

    /** Google's permanent account id ("sub" claim). Null if never signed in with Google. */
    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;
    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;
    protected User(){}
    public User(String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
    }

    /** A new account created by signing in with Google: no password. */
    public static User fromGoogle(String email, String googleSubject) {
        User user = new User(email, null);
        user.linkGoogleAccount(googleSubject);
        return user;
    }

    /**
     * Connect a Google account to this user. Signing in with the same Google account
     * again is fine; a DIFFERENT Google account is refused, so nobody can quietly attach
     * their Google login to someone else's ReturnMate account.
     */
    public void linkGoogleAccount(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Google subject is required");
        }
        if (googleSubject != null && !googleSubject.equals(subject)) {
            throw new IllegalStateException("This account is already linked to a different Google account");
        }
        this.googleSubject = subject;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }
    private String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException(
                    "Email cannot be null"
            );
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }


}
