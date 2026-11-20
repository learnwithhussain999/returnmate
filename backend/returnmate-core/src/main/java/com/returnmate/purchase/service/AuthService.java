package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.LoginRequest;
import com.returnmate.purchase.dto.LoginResponse;
import com.returnmate.purchase.dto.RegistrationRequest;
import com.returnmate.purchase.dto.RegistrationResponse;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.exceptions.EmailAlreadyExistsException;
import com.returnmate.purchase.repository.UserRepository;
import com.returnmate.purchase.security.JwtService;
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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(
                    "Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(token, "Bearer");
    }

}
