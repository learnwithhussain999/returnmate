package com.returnmate.purchase.exceptions;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timeStamp,
        int status,
        String error,
        String message,
        String path,
        Map<String,String> validationErrors
){}
