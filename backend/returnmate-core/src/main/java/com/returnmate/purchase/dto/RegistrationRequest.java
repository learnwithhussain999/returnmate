package com.returnmate.purchase.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 320, message = "Email cannot exceed 320 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 12, message = "Password must contain at least 12 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9\\s])\\S+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character, with no whitespace"
        )
        String password
){
}
