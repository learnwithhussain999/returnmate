
package com.returnmate.purchase.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader(HttpHeaders.AUTHORIZATION);

        // No Bearer token: continue without authentication.
        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        try {
            Claims claims = jwtService.parseClaims(token);

            String email = claims.getSubject();

            String userIdClaim =
                    claims.get("userId", String.class);

            if (email == null || userIdClaim == null) {
                throw new IllegalArgumentException(
                        "Required JWT claims are missing");
            }

            UUID userId = UUID.fromString(userIdClaim);

            // Avoid replacing an existing authentication.
            if (SecurityContextHolder.getContext()
                    .getAuthentication() == null) {

                AuthenticatedUser authenticatedUser =
                        new AuthenticatedUser(userId, email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                authenticatedUser,
                                null,
                                List.of()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContext context =
                        SecurityContextHolder.createEmptyContext();

                context.setAuthentication(authentication);

                SecurityContextHolder.setContext(context);
            }

        } catch (JwtException | IllegalArgumentException exception) {

            // Invalid, expired or malformed token.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
