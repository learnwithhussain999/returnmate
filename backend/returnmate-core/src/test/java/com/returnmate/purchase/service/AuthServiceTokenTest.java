package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.LoginRequest;
import com.returnmate.purchase.dto.LoginResponse;
import com.returnmate.purchase.dto.RefreshTokenRequest;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.repository.UserRepository;
import com.returnmate.purchase.security.InvalidRefreshTokenException;
import com.returnmate.purchase.security.JwtService;
import com.returnmate.purchase.security.RefreshTokenService;
import com.returnmate.purchase.security.google.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTokenTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GoogleIdTokenVerifier googleVerifier;
    @Mock private RefreshTokenService refreshTokens;

    private AuthService authService;
    private final User user = new User("friend@gmail.com", "bcrypt-hash");

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, googleVerifier, refreshTokens);
    }

    @Test
    void loginReturnsAnAccessTokenAndARefreshToken() {
        when(userRepository.findByEmail("friend@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "bcrypt-hash")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("access-jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);
        when(refreshTokens.issue(user)).thenReturn("rmrt_first");

        LoginResponse response = authService.login(new LoginRequest("friend@gmail.com", "pw"));

        assertEquals(new LoginResponse("access-jwt", "Bearer", 900, "rmrt_first"), response);
    }

    @Test
    void refreshReturnsTheRotatedTokenAndANewAccessToken() {
        when(refreshTokens.rotate("rmrt_old")).thenReturn(new RefreshTokenService.Rotation(user, "rmrt_new"));
        when(jwtService.generateToken(user)).thenReturn("new-access-jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);

        LoginResponse response = authService.refresh(new RefreshTokenRequest("rmrt_old"));

        assertEquals("new-access-jwt", response.accessToken());
        assertEquals("rmrt_new", response.refreshToken());
        verify(refreshTokens, never()).issue(any()); // same session, not a new one
    }

    @Test
    void invalidRefreshTokenIssuesNothing() {
        when(refreshTokens.rotate("rmrt_bad")).thenThrow(new InvalidRefreshTokenException());

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(new RefreshTokenRequest("rmrt_bad")));
        verifyNoInteractions(jwtService);
    }

    @Test
    void logoutRevokesTheSession() {
        authService.logout(new RefreshTokenRequest("rmrt_current"));
        verify(refreshTokens).revoke("rmrt_current");
    }
}
