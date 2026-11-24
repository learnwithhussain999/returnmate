package com.returnmate.privacy.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Replaces Logback's {@code %m} / {@code %msg} / {@code %message}: prints the log
 * message after {@link LogMasker} has removed secrets. Registered in logback-spring.xml.
 *
 * <p>{@code getFormattedMessage()} is the message with its {@code {}} placeholders
 * filled in, which is where accidental secrets usually come from:
 * {@code log.info("token={}", token)}.
 */
public class MaskingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.mask(event.getFormattedMessage());
    }
}
