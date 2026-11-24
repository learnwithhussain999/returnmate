package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.GoogleLoginRequest;
import com.returnmate.purchase.dto.LoginRequest;
import com.returnmate.purchase.dto.LoginResponse;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.repository.UserRepository;
import com.returnmate.purchase.security.JwtService;
import com.returnmate.purchase.security.google.GoogleIdTokenVerifier;
import com.returnmate.purchase.security.google.GoogleIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    private static final String SUBJECT = "110169484474386276334";
    private static final String ID_TOKEN = "google-id-token";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GoogleIdTokenVerifier googleVerifier;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, googleVerifier);
    }

    private void googleSays(String email, boolean verified) {
        when(googleVerifier.verify(ID_TOKEN)).thenReturn(new GoogleIdentity(SUBJECT, email, verified));
    }

    private LoginResponse signIn() {
        return authService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));
    }

    @Test
    void firstGoogleSignInCreatesAnAccountWithoutAPassword() {
        googleSays("Friend@Gmail.com", true);
        when(userRepository.findByGoogleSubject(SUBJECT)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("friend@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("our-jwt");

        LoginResponse response = signIn();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("friend@gmail.com", saved.getValue().getEmail());
        assertEquals(SUBJECT, saved.getValue().getGoogleSubject());
        assertFalse(saved.getValue().hasPassword());
        assertEquals("our-jwt", response.accessToken());
    }

    @Test
    void returningGoogleUserIsFoundByGoogleIdEvenIfTheirEmailChanged() {
        User existing = User.fromGoogle("old-address@gmail.com", SUBJECT);
        googleSays("new-address@gmail.com", true);
        when(userRepository.findByGoogleSubject(SUBJECT)).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing)).thenReturn("our-jwt");

        signIn();

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordUserWithSameVerifiedEmailGetsLinked() {
        User passwordUser = new User("friend@gmail.com", "bcrypt-hash");
        googleSays("friend@gmail.com", true);
        when(userRepository.findByGoogleSubject(SUBJECT)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("friend@gmail.com")).thenReturn(Optional.of(passwordUser));
        when(jwtService.generateToken(passwordUser)).thenReturn("our-jwt");

        signIn();

        assertEquals(SUBJECT, passwordUser.getGoogleSubject());
        assertTrue(passwordUser.hasPassword(), "their password still works too");
    }

    @Test
    void unverifiedGoogleEmailIsRefusedAndNothingIsLinked() {
        // Someone made a Google account with YOUR email but never proved they own it.
        googleSays("victim@example.com", false);

        assertThrows(BadCredentialsException.class, this::signIn);
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void emailAlreadyLinkedToADifferentGoogleAccountIsRefused() {
        User linkedElsewhere = User.fromGoogle("friend@gmail.com", "some-other-google-id");
        googleSays("friend@gmail.com", true);
        when(userRepository.findByGoogleSubject(SUBJECT)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("friend@gmail.com")).thenReturn(Optional.of(linkedElsewhere));

        assertThrows(BadCredentialsException.class, this::signIn);
        assertEquals("some-other-google-id", linkedElsewhere.getGoogleSubject());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void passwordLoginForGoogleOnlyAccountFailsLikeAWrongPassword() {
        User googleOnly = User.fromGoogle("friend@gmail.com", SUBJECT);
        when(userRepository.findByEmail("friend@gmail.com")).thenReturn(Optional.of(googleOnly));

        BadCredentialsException e = assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("friend@gmail.com", "anything")));
        assertEquals("Invalid email or password", e.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
