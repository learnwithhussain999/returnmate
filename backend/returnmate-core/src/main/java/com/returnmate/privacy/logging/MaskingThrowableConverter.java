package com.returnmate.privacy.logging;

import ch.qos.logback.classic.spi.IThrowableProxy;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/**
 * Replaces Spring Boot's {@code %wEx}: prints stack traces after masking them.
 *
 * <p>Exception messages are a common leak. A library throws
 * {@code IllegalArgumentException("Invalid refresh token 1//0gAbc...")}, and the default
 * stack-trace printer writes the token into the log. We extend Spring Boot's own
 * converter, so the layout stays the same and only the text is masked.
 */
public class MaskingThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy throwableProxy) {
        return LogMasker.mask(super.throwableProxyToString(throwableProxy));
    }
}
