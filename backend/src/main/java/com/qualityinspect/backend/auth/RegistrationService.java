package com.qualityinspect.backend.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.auth.AuthDtos.AuthResponse;
import com.qualityinspect.backend.auth.RegistrationDtos.*;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.config.AppProperties;
import com.qualityinspect.backend.security.JwtService;
import com.qualityinspect.backend.user.RoleEntity;
import com.qualityinspect.backend.user.RoleRepository;
import com.qualityinspect.backend.user.UserEntity;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Self-registration with OTP ownership verification for email or phone.
 *
 * Flow: initiate (validate + create user draft + send OTP) -> verify (OTP + password)
 * creates the user with the INSPECTOR role and returns a normal login response.
 *
 * Delivery is pluggable via {@link #deliver}; the console logger keeps the demo
 * self-contained, and app.registration.dev-otp-in-response additionally returns
 * the code in the API response for development builds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,64}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegistrationOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final AuditService auditService;
    private final AuthService authService;
    private final OtpEmailService otpEmailService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OtpResponse initiate(RegisterRequest request) {
        String username = trim(request.username());
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim();
        String contact = trim(request.contact());
        validateCommon(username, request.password(), contact);
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken");
        }
        RegistrationPayload payload = new RegistrationPayload(
                username, displayName, passwordEncoder.encode(request.password()), contact, contactType(contact));
        return issueOtp(contact, payload);
    }

    @Transactional
    public OtpResponse resend(String contactRaw) {
        String contact = trim(contactRaw);
        RegistrationOtpEntity latest = otpRepository
                .findFirstByContactAndConsumedAtIsNullOrderByIdDesc(contact)
                .orElseThrow(() -> new BadRequestException("No pending registration for this contact"));
        RegistrationPayload payload = readPayload(latest.getPayload());
        return issueOtp(contact, payload);
    }

    @Transactional
    public AuthResponse verify(OtpVerifyRequest request) {
        String contact = trim(request.contact());
        String otp = trim(request.otp());
        RegistrationOtpEntity row = otpRepository
                .findFirstByContactAndConsumedAtIsNullOrderByIdDesc(contact)
                .orElseThrow(() -> new BadRequestException("No pending registration for this contact"));
        int maxAttempts = appProperties.getRegistration().getMaxAttempts();
        if (row.getAttempts() >= maxAttempts) {
            row.setConsumedAt(Instant.now());
            otpRepository.save(row);
            throw new BadRequestException("Too many incorrect attempts; please request a new code");
        }
        if (row.isExpired()) {
            throw new BadRequestException("OTP has expired; please request a new code");
        }
        row.setAttempts(row.getAttempts() + 1);
        if (!jwtService.sha256(otp).equals(row.getCodeHash())) {
            otpRepository.save(row);
            throw new BadRequestException("Incorrect OTP");
        }
        row.setConsumedAt(Instant.now());
        otpRepository.save(row);

        RegistrationPayload payload = readPayload(row.getPayload());
        if (userRepository.existsByUsername(payload.username())) {
            throw new ConflictException("Username is already taken");
        }
        RoleEntity inspector = roleRepository.findByCode("INSPECTOR")
                .orElseThrow(() -> new IllegalStateException("INSPECTOR role missing"));

        UserEntity user = new UserEntity();
        user.setOrgId("default");
        user.setUsername(payload.username());
        user.setDisplayName(payload.displayName());
        user.setPasswordHash(payload.passwordHash());
        if ("email".equals(payload.contactType())) {
            user.setEmail(payload.contact());
        } else {
            user.setPhone(payload.contact());
        }
        user.setEnabled(true);
        user.getRoles().add(inspector);
        user = userRepository.save(user);

        auditService.record("USER", user.getId(), "REGISTER_SELF",
                "Self-registration verified via " + payload.contactType(), null);
        return authService.issueTokensFor(user);
    }

    private OtpResponse issueOtp(String contact, RegistrationPayload payload) {
        for (RegistrationOtpEntity old : otpRepository.findByContactAndConsumedAtIsNull(contact)) {
            old.setConsumedAt(Instant.now());
            otpRepository.save(old);
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        long ttlMinutes = appProperties.getRegistration().getOtpTtlMinutes();

        RegistrationOtpEntity row = new RegistrationOtpEntity();
        row.setContact(contact);
        row.setCodeHash(jwtService.sha256(code));
        row.setPayload(toJson(payload));
        row.setAttempts(0);
        row.setExpiresAt(Instant.now().plusSeconds(ttlMinutes * 60));
        otpRepository.save(row);

        deliver(contact, code, payload);
        log.info("Registration OTP issued for {} (valid {} min)", mask(contact), ttlMinutes);
        boolean devOtp = appProperties.getRegistration().isDevOtpInResponse();
        return new OtpResponse("OTP sent to " + mask(contact), mask(contact), devOtp ? code : null);
    }

    /**
     * Delivers the OTP by email using the Defectra AI support template.
     * SMTP failures fall back to console delivery so the flow always works.
     */
    private void deliver(String contact, String code, RegistrationPayload payload) {
        if (otpEmailService.sendOtp(contact, payload.username(), code)) {
            return;
        }
        log.info("OTP delivery [{}]: {}", contact, code);
    }

    private void validateCommon(String username, String password, String contact) {
        if (!USERNAME.matcher(username).matches()) {
            throw new BadRequestException("Username must be 3-64 characters (letters, digits, dot, underscore, dash)");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BadRequestException("Password must be 8-128 characters");
        }
        if (!EMAIL.matcher(contact).matches()) {
            throw new BadRequestException("Contact must be a valid email address");
        }
    }

    private String contactType(String contact) {
        // phone registration removed: email is the only OTP delivery channel
        return EMAIL.matcher(contact).matches() ? "email" : null;
    }

    private String mask(String contact) {
        int at = contact.indexOf('@');
        if (at > 0) return contact.charAt(0) + "***" + contact.substring(at);
        if (contact.length() <= 5) return "***";
        return contact.substring(0, 3) + "*****" + contact.substring(contact.length() - 2);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String toJson(RegistrationPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize registration payload");
        }
    }

    private RegistrationPayload readPayload(String json) {
        try {
            return objectMapper.readValue(json, RegistrationPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Registration payload is corrupt");
        }
    }
}
