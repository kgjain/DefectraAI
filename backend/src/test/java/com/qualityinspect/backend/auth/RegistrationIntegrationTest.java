package com.qualityinspect.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Self-registration with OTP verification tests (public endpoints, no auth).
 * Test profile has app.registration.dev-otp-in-response=true so the OTP is
 * readable from the initiate response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String initiate(String username, String password, String displayName, String contact) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\","
                                + "\"displayName\":\"" + displayName + "\",\"contact\":\"" + contact + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devOtp").value(matchesPattern("^[0-9]{6}$")))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("devOtp").asText();
    }

    private void verify(String contact, String otp, String password, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contact\":\"" + contact + "\",\"otp\":\"" + otp + "\","
                                + "\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    // ---- happy paths ----

    @Test
    void registerWithEmail_verify_returnsTokens_andUserIsActiveInspector() throws Exception {
        String otp = initiate("reg_alice", "Password1!", "Alice Chen", "alice@example.com");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contact\":\"alice@example.com\",\"otp\":\"" + otp + "\","
                                + "\"password\":\"Password1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("reg_alice"))
                .andExpect(jsonPath("$.user.roles[0]").value("INSPECTOR"))
                .andExpect(jsonPath("$.user.enabled").value(true))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("reg_alice"));
    }

    @Test
    void register_withPhone_returns400_emailOnly() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reg_bob\",\"password\":\"Password1!\","
                                + "\"displayName\":\"Bob Rao\",\"contact\":\"+919876543210\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- OTP validation ----

    @Test
    void verify_wrongOtp_returns400_thenCorrectOtpWorks() throws Exception {
        String otp = initiate("reg_carol", "Password1!", "Carol", "carol@example.com");
        verify("carol@example.com", "000000".equals(otp) ? "999999" : "000000", "Password1!", 400);
        verify("carol@example.com", otp, "Password1!", 200);
    }

    @Test
    void verify_tooManyWrongAttempts_otpIsBurned() throws Exception {
        String otp = initiate("reg_dave", "Password1!", "Dave", "dave@example.com");
        String wrong = "000000".equals(otp) ? "999999" : "000000";
        for (int i = 0; i < 5; i++) {
            verify("dave@example.com", wrong, "Password1!", 400);
        }
        verify("dave@example.com", otp, "Password1!", 400);
    }

    @Test
    void resend_invalidatesPreviousOtp() throws Exception {
        String first = initiate("reg_erin", "Password1!", "Erin", "erin@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contact\":\"erin@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devOtp").value(matchesPattern("^[0-9]{6}$")))
                .andReturn();
        String second = objectMapper.readTree(result.getResponse().getContentAsString()).get("devOtp").asText();

        verify("erin@example.com", first, "Password1!", 400);
        verify("erin@example.com", second, "Password1!", 200);
    }

    @Test
    void verify_withoutPendingRegistration_returns400() throws Exception {
        verify("nobody@example.com", "123456", "Password1!", 400);
    }

    @Test
    void resend_withoutPendingRegistration_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contact\":\"nobody@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- validation ----

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        String otp = initiate("reg_frank", "Password1!", "Frank", "frank@example.com");
        verify("frank@example.com", otp, "Password1!", 200);
        // username is now taken; a second registration for it is rejected at initiate
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reg_frank\",\"password\":\"Password1!\","
                                + "\"displayName\":\"Frank2\",\"contact\":\"frank2@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void verify_afterUsernameTaken_returns409() throws Exception {
        // two pending registrations for the same username: first verify wins, second gets 409
        String otp1 = initiate("reg_racer", "Password1!", "Racer", "racer1@example.com");
        String otp2 = initiate("reg_racer", "Password1!", "Racer", "racer2@example.com");
        verify("racer1@example.com", otp1, "Password1!", 200);
        verify("racer2@example.com", otp2, "Password1!", 409);
    }

    @Test
    void register_invalidContact_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reg_grace\",\"password\":\"Password1!\","
                                + "\"displayName\":\"Grace\",\"contact\":\"not-a-contact\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reg_henry\",\"password\":\"short\","
                                + "\"displayName\":\"Henry\",\"contact\":\"henry@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a b\",\"password\":\"Password1!\","
                                + "\"displayName\":\"Bad\",\"contact\":\"bad@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_endpoints_arePublic_noTokenNeeded() throws Exception {
        // initiate already returns 200 without any Authorization header (covered above);
        // verify explicitly for the verify endpoint with garbage input -> 400 not 401
        mockMvc.perform(post("/api/v1/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contact\":\"x@y.z\",\"otp\":\"1\",\"password\":\"whatever\"}"))
                .andExpect(status().isBadRequest());
    }
}
