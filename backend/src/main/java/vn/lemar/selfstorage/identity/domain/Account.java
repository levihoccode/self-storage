package vn.lemar.selfstorage.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email; // normalized: lowercase + trim trước khi lưu/so sánh

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.UNVERIFIED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
        // JPA
    }

    public Account(String email, String passwordHash, Role role) {
        this.email = normalize(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = AccountStatus.UNVERIFIED;
    }

    public static String normalize(String rawEmail) {
        return rawEmail == null ? null : rawEmail.trim().toLowerCase();
    }

    public boolean isVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isLoginAllowed() {
        return status == AccountStatus.ACTIVE;
    }

    public void markEmailVerified() {
        this.emailVerifiedAt = Instant.now();
        if (this.status == AccountStatus.UNVERIFIED) {
            this.status = AccountStatus.ACTIVE;
        }
    }

    // --- Getters ---

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}