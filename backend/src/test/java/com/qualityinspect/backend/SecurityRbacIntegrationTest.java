package com.qualityinspect.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.user.RoleEntity;
import com.qualityinspect.backend.user.RoleRepository;
import com.qualityinspect.backend.user.UserEntity;
import com.qualityinspect.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end security + RBAC tests against the qualityinspect_test database.
 * Users are seeded directly (bootstrap is single-use and reserved for the real app).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityRbacIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    String inspectorToken;
    String managerToken;

    @BeforeEach
    void seedUsers() {
        RoleEntity inspectorRole = roleRepository.findByCode("INSPECTOR").orElseThrow();
        RoleEntity managerRole = roleRepository.findByCode("QUALITY_MANAGER").orElseThrow();

        UserEntity inspector = upsert("it_inspector", "Inspector!123", inspectorRole);
        UserEntity manager = upsert("it_manager", "Manager!12345", managerRole);

        inspectorToken = login("it_inspector", "Inspector!123");
        managerToken = login("it_manager", "Manager!12345");
    }

    private UserEntity upsert(String username, String password, RoleEntity role) {
        return userRepository.findByUsername(username).map(u -> {
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setEnabled(true);
            u.getRoles().clear();
            u.getRoles().add(role);
            return userRepository.save(u);
        }).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setOrgId("default");
            u.setUsername(username);
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setDisplayName(username);
            u.setEnabled(true);
            u.getRoles().add(role);
            return userRepository.save(u);
        });
    }

    private String login(String username, String password) {
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- 1. health is public ----
    @Test
    void health_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    // ---- 2. protected endpoint without token -> 401 ----
    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 3. protected endpoint with garbage token -> 401 ----
    @Test
    void protectedEndpoint_garbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 4. inspector cannot create products (403) ----
    @Test
    void inspector_cannotCreateProduct_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"IT-PROD-1\",\"name\":\"Widget\",\"category\":\"PARTS\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- 5. inspector cannot read the audit trail (403) ----
    @Test
    void inspector_cannotReadAudit_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isForbidden());
    }

    // ---- 6. manager can create products (201) ----
    @Test
    void manager_canCreateProduct_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"IT-PROD-2\",\"name\":\"Widget\",\"category\":\"PARTS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("IT-PROD-2"));
    }

    // ---- 7. manager can read the audit trail (200) ----
    @Test
    void manager_canReadAudit_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    // ---- 8. inspector can list products (200) ----
    @Test
    void inspector_canListProducts_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isOk());
    }

    // ---- 9. invalid login credentials -> 401 ----
    @Test
    void invalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it_inspector\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 10. /me returns the authenticated user ----
    @Test
    void me_returnsAuthenticatedUser() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("it_manager"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.get("roles").toString().contains("QUALITY_MANAGER"));
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
