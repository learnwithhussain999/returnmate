package com.returnmate.purchase.security;

import com.returnmate.purchase.entity.RefreshToken;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens: long-lived (30 days by default), random, stored only as a hash, and
 * ROTATED on every use.
 *
 * <pre>
 *   sign in        -> token A                     (new family F)
 *   refresh with A -> A retired, token B issued   (family F)
 *   refresh with B -> B retired, token C issued   (family F)
 *   refresh with A -> A was already used: someone has a copy. Revoke ALL of family F.
 * </pre>
 *
 * The last line is "reuse detection": a stolen token works at most until the real user
 * (or the thief) refreshes once, and then everyone in that session has to sign in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** Lets people, log masking and secret scanners recognise our tokens. */
    public static final String PREFIX = "rmrt_";
    private static final int RANDOM_BYTES = 32; // 256 bits

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration timeToLive;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            Clock clock,
            @Value("${app.jwt.refresh-token-ttl:P30D}") Duration timeToLive) {
        this.repository = repository;
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    /** A fresh token for a new sign-in. Returns the raw token: the only time it exists. */
    @Transactional
    public String issue(User user) {
        return issueInFamily(user, UUID.randomUUID());
    }

    /** The user and their next refresh token. */
    public record Rotation(User user, String refreshToken) {
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presentedToken) {
        RefreshToken current = findForUpdate(presentedToken);
        Instant now = clock.instant();

        if (current.isRevoked()) {
            // Already used (or logged out). Revoking the family must COMMIT even though we
            // throw, hence noRollbackFor above.
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} active token(s) in its session",
                    current.getUser().getId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        current.revoke(now);
        String next = issueInFamily(current.getUser(), current.getFamilyId());
        return new Rotation(current.getUser(), next);
    }

    /** Log out: end this sign-in session. Unknown tokens are ignored, so logout always "works". */
    @Transactional
    public void revoke(String presentedToken) {
        if (!looksLikeOurToken(presentedToken)) {
            return;
        }
        repository.findByTokenHashForUpdate(hash(presentedToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private String issueInFamily(User user, UUID familyId) {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        repository.save(new RefreshToken(user, hash(token), familyId, now, now.plus(timeToLive)));
        return token;
    }

    private RefreshToken findForUpdate(String presentedToken) {
        if (!looksLikeOurToken(presentedToken)) {
            throw new InvalidRefreshTokenException();
        }
        return repository.findByTokenHashForUpdate(hash(presentedToken))
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    private static boolean looksLikeOurToken(String token) {
        return token != null && token.startsWith(PREFIX) && token.length() < 200;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
