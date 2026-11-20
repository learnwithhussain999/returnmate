package com.returnmate.purchase.security;

import java.util.UUID;

public record AuthenticatedUser (
        UUID userId,
        String email
){
}
