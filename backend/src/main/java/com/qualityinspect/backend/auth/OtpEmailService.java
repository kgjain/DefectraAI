package com.qualityinspect.backend.auth;

import com.qualityinspect.backend.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Sends the registration OTP email using the Defectra AI support template.
 *
 * Requires SMTP credentials (Gmail app password): set MAIL_PASSWORD in the
 * environment. If sending fails or email is disabled, returns false so the
 * caller can fall back to console delivery and keep the flow working.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpEmailService {

    private static final String SUBJECT = "Your Defectra AI verification code";

    private static final String TEMPLATE = """
            Dear {{username}},

            Your OTP for verifying your Defectra AI account is:

            {{OTP}}

            Expires in: {{ttl}} minutes from issuance

            Security: Do not share this OTP with anyone.

            Need help? Contact our support team at {{support_email}}.

            If you did not request this verification, please ignore this email.

            Regards,
            Defectra AI Support Team
            """;

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @PostConstruct
    void checkMailConfig() {
        AppProperties.Registration cfg = appProperties.getRegistration();
        if (!cfg.isEmailEnabled()) {
            log.warn("OTP email sending is DISABLED (app.registration.email-enabled=false)");
        } else if (mailPassword == null || mailPassword.isBlank()) {
            log.error("MAIL_PASSWORD is NOT set — OTP emails CANNOT be sent! "
                    + "Users' OTPs will only appear in this server console. "
                    + "Start the backend with: set MAIL_PASSWORD=<gmail-app-password>");
        } else {
            log.info("OTP email enabled — sending as {} ({})", cfg.getMailFrom(), "smtp.gmail.com:587");
        }
    }

    /** @return true if the email was accepted for delivery */
    public boolean sendOtp(String toEmail, String username, String code) {
        AppProperties.Registration cfg = appProperties.getRegistration();
        if (!cfg.isEmailEnabled()) {
            log.info("OTP email disabled by config; skipping send to {}", toEmail);
            return false;
        }
        if (mailPassword == null || mailPassword.isBlank()) {
            log.warn("OTP email to {} NOT sent: MAIL_PASSWORD is not set on this backend", toEmail);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(cfg.getMailFrom(), cfg.getMailFromName());
            helper.setTo(toEmail);
            helper.setSubject(SUBJECT);
            helper.setText(render(username, code), false);
            mailSender.send(message);
            log.info("OTP email sent to {} (valid {} min)", toEmail, cfg.getOtpTtlMinutes());
            return true;
        } catch (Exception e) {
            log.warn("OTP email delivery failed to {} ({}); using console fallback",
                    toEmail, e.getMessage());
            return false;
        }
    }

    private String render(String username, String code) {
        AppProperties.Registration cfg = appProperties.getRegistration();
        return TEMPLATE
                .replace("{{username}}", username)
                .replace("{{OTP}}", code)
                .replace("{{ttl}}", String.valueOf(cfg.getOtpTtlMinutes()))
                .replace("{{support_email}}", cfg.getSupportEmail());
    }
}
