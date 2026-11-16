package com.example.qsale.auth.domain;

import com.example.qsale.auth.components.JwtService;
import com.example.qsale.auth.dto.TokenResponse;
import com.example.qsale.auth.infrastructure.AuthSessionRepository;
import com.example.qsale.exceptions.UnauthorizedException;
import com.example.qsale.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private final AuthSessionRepository sessions;
    private final JwtService jwt;

    @Transactional
    public TokenResponse issue(User user) {
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID().toString());
        session.setUser(user);
        TokenResponse tokens = rotateTokens(session);
        sessions.save(session);
        return tokens;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse refresh(String token) {
        if (!jwt.isRefreshTokenValid(token)) throw rejected();
        String id = jwt.extractSessionId(token);
        if (id == null) throw rejected();
        AuthSession session = sessions.findForUpdate(id).orElseThrow(this::rejected);
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) throw rejected();
        if (!MessageDigest.isEqual(session.getRefreshHash().getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII))) {
            session.setRevokedAt(Instant.now());
            throw rejected();
        }
        return rotateTokens(session);
    }

    @Transactional
    public void revoke(String refreshToken) {
        if (!jwt.isRefreshTokenValid(refreshToken)) return;
        String id = jwt.extractSessionId(refreshToken);
        if (id != null) sessions.findForUpdate(id).ifPresent(session -> session.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public boolean isActive(String token) {
        try {
            String id = jwt.extractSessionId(token);
            return id != null && sessions.existsByIdAndUserEmailAndRevokedAtIsNullAndExpiresAtAfter(
                    id, jwt.extractUsername(token), Instant.now());
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${auth.session-cleanup-delay:86400000}")
    @Transactional
    public void removeExpiredSessions() {
        sessions.deleteByExpiresAtBefore(Instant.now());
    }

    private TokenResponse rotateTokens(AuthSession session) {
        String refresh = jwt.generateRefreshToken(session.getUser(), session.getId());
        session.setRefreshHash(hash(refresh));
        session.setExpiresAt(jwt.extractExpiration(refresh));
        return new TokenResponse(jwt.generateAccessToken(session.getUser(), session.getId()), refresh);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private UnauthorizedException rejected() {
        return new UnauthorizedException("Refresh token is invalid or expired");
    }
}
