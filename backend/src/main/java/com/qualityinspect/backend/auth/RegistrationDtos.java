package com.qualityinspect.backend.auth;

public final class RegistrationDtos {

    public record RegisterRequest(String username, String password, String displayName, String contact) {}

    public record OtpVerifyRequest(String contact, String otp, String password) {}

    public record ResendRequest(String contact) {}

    public record OtpResponse(String message, String contactMasked, String devOtp) {}

    record RegistrationPayload(String username, String displayName, String passwordHash,
                               String contact, String contactType) {}
}
