package dev.hrai.jobposting.email;

/**
 * Sends the screening-interview email triggered when HR approves a
 * candidate. Kept as an interface so the provider (SMTP today, Gmail API
 * once that's built for inbox-monitoring, anything else later) is a
 * swappable implementation detail -- see {@link LoggingEmailSender} (the
 * safe default) and {@link SmtpEmailSender}.
 */
public interface EmailSender {

    /**
     * @throws Exception if the email could not be sent. Callers should
     *                    surface this to HR rather than silently treat the
     *                    candidate as contacted.
     */
    void sendScreeningInvite(String toEmail, String toName, String postingTitle) throws Exception;
}
