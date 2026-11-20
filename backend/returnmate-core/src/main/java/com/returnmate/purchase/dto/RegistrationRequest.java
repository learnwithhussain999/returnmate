package com.returnmate.purchase.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 320, message = "Email cannot exceed 320 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 12, message = "Password must contain at least 12 characters")
        String password
){
}
