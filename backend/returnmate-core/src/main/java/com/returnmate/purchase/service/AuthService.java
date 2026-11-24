package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.GoogleLoginRequest;
import com.returnmate.purchase.dto.LoginRequest;
import com.returnmate.purchase.dto.LoginResponse;
import com.returnmate.purchase.dto.RegistrationRequest;
import com.returnmate.purchase.dto.RegistrationResponse;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.exceptions.EmailAlreadyExistsException;
import com.returnmate.purchase.repository.UserRepository;
import com.returnmate.purchase.security.JwtService;
import com.returnmate.purchase.security.google.GoogleIdTokenVerifier;
import com.returnmate.purchase.security.google.GoogleIdentity;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleVerifier;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       GoogleIdTokenVerifier googleVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleVerifier = googleVerifier;
    }
    @Transactional
    public RegistrationResponse register(RegistrationRequest request){
            String normalizedEmail=request.email().trim().toLowerCase(Locale.ROOT);
            if(userRepository.existsByEmail(normalizedEmail)){
                throw new EmailAlreadyExistsException("An Account with this email already exists");
            }
            // BCrypt password supports maximum 72 bytes input
            int passwordBytes=request.password().getBytes(StandardCharsets.UTF_8).length;
            if (passwordBytes > 72) {
            throw new IllegalArgumentException(
                    "Password cannot exceed 72 UTF-8 bytes");
            }
            String passwordHash=passwordEncoder.encode(request.password());
            User user = new User(normalizedEmail, passwordHash);
            User userSaved=userRepository.save(user);

            return new RegistrationResponse(
                    userSaved.getId(),
                    userSaved.getEmail(),
                    userSaved.getCreatedAt()
            );

    }
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email()
                .trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException(
                        "Invalid email or password"));

        // Google-only accounts have no password. Same message as a wrong password, so the
        // response doesn't reveal which sign-in method an email address uses.
        if (!user.hasPassword()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(
                    "Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(token, "Bearer");
    }

    /**
     * Sign in with Google. Finds the user in this order:
     * <ol>
     *   <li>by Google account id: they've signed in with Google before;</li>
     *   <li>by email: they registered with a password, and now use Google with the same
     *       address. We link the accounts, but ONLY because Google says it has verified
     *       that email. Linking on an unverified email would let anyone who creates a
     *       Google account with your address take over your ReturnMate account;</li>
     *   <li>otherwise a new account with no password.</li>
     * </ol>
     */
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdentity google = googleVerifier.verify(request.idToken());
        if (!google.emailVerified()) {
            throw new BadCredentialsException("Your Google account's email address is not verified");
        }
        String email = google.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByGoogleSubject(google.subject())
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> link(existing, google.subject()))
                        .orElseGet(() -> userRepository.save(User.fromGoogle(email, google.subject()))));

        return new LoginResponse(jwtService.generateToken(user), "Bearer");
    }

    private User link(User existing, String googleSubject) {
        try {
            existing.linkGoogleAccount(googleSubject); // saved at commit (JPA dirty checking)
            return existing;
        } catch (IllegalStateException alreadyLinkedElsewhere) {
            throw new BadCredentialsException("This email is already linked to a different Google account");
        }
    }
}
