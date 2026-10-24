package dev.hrai.jobposting.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link EmailSender}: logs what would have been sent instead of
 * actually sending it. Active whenever no real provider (e.g. SMTP) is
 * configured, so approving a candidate never silently fails or requires
 * credentials just to try the feature out.
 */
public final class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendScreeningInvite(String toEmail, String toName, String postingTitle) {
        log.info(
                "[no email provider configured] Would send screening-interview invite to {} <{}> for '{}'",
                toName, toEmail, postingTitle
        );
    }
}
