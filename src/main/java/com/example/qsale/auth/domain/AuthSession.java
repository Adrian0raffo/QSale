package com.example.qsale.auth.domain;

import com.example.qsale.user.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AuthSession {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String refreshHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;
}
