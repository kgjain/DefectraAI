package com.qualityinspect.backend.auth;

import com.qualityinspect.backend.auth.AuthDtos.AuthResponse;
import com.qualityinspect.backend.auth.RegistrationDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    public OtpResponse initiate(@RequestBody RegisterRequest request) {
        return registrationService.initiate(request);
    }

    @PostMapping("/verify")
    public AuthResponse verify(@RequestBody OtpVerifyRequest request) {
        return registrationService.verify(request);
    }

    @PostMapping("/resend")
    public OtpResponse resend(@RequestBody ResendRequest request) {
        return registrationService.resend(request.contact());
    }
}
