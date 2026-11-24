package com.returnmate.purchase.exceptions;

import com.returnmate.purchase.security.InvalidRefreshTokenException;
import com.returnmate.purchase.security.google.GoogleSignInNotConfiguredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException exception,
                                                                HttpServletRequest request){
        ErrorResponse response=new ErrorResponse( Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                Map.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // 400 - Invalid path variable or parameter
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid value for parameter: " + exception.getName(),
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.badRequest().body(response);
    }
    // 400 - Request validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();

        for (FieldError fieldError :
                exception.getBindingResult().getFieldErrors()) {

            validationErrors.putIfAbsent(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request validation failed",
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    // 400 - Invalid JSON request
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Invalid request body or JSON format",
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.badRequest().body(response);
    }

    // 400 - Illegal arguments
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.badRequest().body(response);
    }

    // 500 - Unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception exception,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred",
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.internalServerError().body(response);
    }

    // Duplicate user exception handling
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException exception,HttpServletRequest request)
    {
        ErrorResponse response=new ErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()

        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request) {

        // Password login keeps its generic message; Google sign-in messages say what to fix.
        String message = request.getRequestURI().endsWith("/auth/google")
                ? exception.getMessage()
                : "Invalid email or password";

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                message,
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // 503 - Google sign-in switched off (no GOOGLE_CLIENT_ID)
    @ExceptionHandler(GoogleSignInNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleGoogleNotConfigured(
            GoogleSignInNotConfiguredException exception,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    // 401 - refresh token unknown, expired, revoked or reused: sign in again
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}
