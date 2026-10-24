package dev.hrai.jobposting.email;

import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * Sends the screening-interview email over plain SMTP with a username/
 * password (e.g. a Gmail app password). Active only when SMTP_HOST,
 * SMTP_USERNAME, and SMTP_PASSWORD are all set -- see App's wiring.
 */
public final class SmtpEmailSender implements EmailSender {

    private final Session session;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(String host, int port, String username, String password, String fromAddress, String fromName) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        this.session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendScreeningInvite(String toEmail, String toName, String postingTitle) throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress, fromName));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("Screening interview -- " + postingTitle + " at Centegy Technologies");
        message.setText(
                "Hi " + toName + ",\n\n"
                        + "Thank you for applying for the " + postingTitle + " role at Centegy Technologies. "
                        + "We'd like to schedule a short screening interview with you.\n\n"
                        + "Please reply to this email with a few times that work for you over the next week, "
                        + "and we'll confirm a slot.\n\n"
                        + "Looking forward to speaking with you.\n\n"
                        + "Best regards,\n"
                        + "Centegy Technologies HR"
        );
        Transport.send(message);
    }
}
