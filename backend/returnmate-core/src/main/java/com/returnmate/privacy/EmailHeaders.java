package com.returnmate.privacy;

/**
 * The only parts of an email we look at BEFORE deciding whether to download its body.
 * Gmail ({@code format=metadata}) and Outlook ({@code $select=from,subject}) can both
 * return these without sending the message body.
 *
 * @param from    the raw From header, e.g. {@code "Chase" <no.reply.alerts@chase.com>}
 * @param subject the raw Subject header; may be null
 */
public record EmailHeaders(String from, String subject) {
}
