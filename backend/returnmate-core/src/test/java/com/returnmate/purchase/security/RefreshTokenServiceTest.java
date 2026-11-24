package com.returnmate.purchase.security;

import com.returnmate.purchase.entity.RefreshToken;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private final User user = new User("friend@gmail.com", "hash");

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(30));
    }

    /** Issue a token and capture the row that was saved for it. */
    private RefreshToken issueAndCapture(String[] rawOut) {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        rawOut[0] = service.issue(user);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        clearInvocations(repository);
        return saved.getValue();
    }

    @Test
    void issuedTokenIsRandomPrefixedAndOnlyItsHashIsStored() {
        String[] raw = new String[1];
        RefreshToken stored = issueAndCapture(raw);

        assertTrue(raw[0].startsWith("rmrt_"));
        assertEquals(5 + 43, raw[0].length()); // prefix + 32 bytes in base64url
        assertEquals(RefreshTokenService.hash(raw[0]), stored.getTokenHash());
        assertNotEquals(raw[0], stored.getTokenHash());
        assertEquals(NOW.plus(Duration.ofDays(30)), stored.getExpiresAt());
    }

    @Test
    void twoSignInsGetDifferentTokensAndDifferentFamilies() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        String a = service.issue(user);
        String b = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(2)).save(saved.capture());
        assertNotEquals(a, b);
        assertNotEquals(saved.getAllValues().get(0).getFamilyId(), saved.getAllValues().get(1).getFamilyId());
    }

    @Test
    void rotateRetiresTheOldTokenAndIssuesANewOneInTheSameFamily() {
        String[] raw = new String[1];
        RefreshToken old = issueAndCapture(raw);
        when(repository.findByTokenHashForUpdate(old.getTokenHash())).thenReturn(Optional.of(old));
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.Rotation rotation = service.rotate(raw[0]);

        assertTrue(old.isRevoked());
        assertNotEquals(raw[0], rotation.refreshToken());
        assertSame(user, rotation.user());
        ArgumentCaptor<RefreshToken> next = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(next.capture());
        assertEquals(old.getFamilyId(), next.getValue().getFamilyId());
    }

    @Test
    void reusingARetiredTokenRevokesTheWholeFamily() {
        String[] raw = new String[1];
        RefreshToken old = issueAndCapture(raw);
        old.revoke(NOW.minusSeconds(60)); // already rotated once
        when(repository.findByTokenHashForUpdate(old.getTokenHash())).thenReturn(Optional.of(old));

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(raw[0]));

        verify(repository).revokeFamily(old.getFamilyId(), NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejected() {
        String[] raw = new String[1];
        RefreshToken token = issueAndCapture(raw);
        RefreshTokenService later = new RefreshTokenService(repository,
                Clock.fixed(NOW.plus(Duration.ofDays(31)), ZoneOffset.UTC), Duration.ofDays(30));
        when(repository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token));

        assertThrows(InvalidRefreshTokenException.class, () -> later.rotate(raw[0]));
        verify(repository, never()).save(any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("rmrt_doesNotExistAnywhere1234567890"));
    }

    @Test
    void somethingThatIsNotOurTokenIsRejectedWithoutTouchingTheDatabase() {
        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("eyJhbGciOi.not.ours"));
        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(null));
        verifyNoInteractions(repository);
    }

    @Test
    void logoutRevokesTheFamily() {
        String[] raw = new String[1];
        RefreshToken token = issueAndCapture(raw);
        when(repository.findByTokenHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token));

        service.revoke(raw[0]);

        verify(repository).revokeFamily(token.getFamilyId(), NOW);
    }

    @Test
    void logoutWithAnUnknownTokenQuietlyDoesNothing() {
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.revoke("rmrt_unknownTokenValue1234567890abc"));
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void reuseRevocationIsNotRolledBackByTheException() throws Exception {
        // The family revocation and the 401 happen together; the @Transactional rule
        // must say "don't roll back" or the revocation would be silently undone.
        var annotation = RefreshTokenService.class.getMethod("rotate", String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertArrayEquals(new Class<?>[]{InvalidRefreshTokenException.class}, annotation.noRollbackFor());
    }
}
