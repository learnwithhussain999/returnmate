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

    @Column(
            name="password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;
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
