package com.returnmate.purchase.controller;

import com.returnmate.purchase.dto.GoogleLoginRequest;
import com.returnmate.purchase.dto.LoginRequest;
import com.returnmate.purchase.dto.LoginResponse;
import com.returnmate.purchase.dto.RefreshTokenRequest;
import com.returnmate.purchase.dto.RegistrationRequest;
import com.returnmate.purchase.dto.RegistrationResponse;
import com.returnmate.purchase.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request
            ){
        RegistrationResponse response=service.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = service.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {

        return ResponseEntity.ok(service.loginWithGoogle(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        return ResponseEntity.ok(service.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {

        service.logout(request);
        return ResponseEntity.noContent().build();
    }
}
