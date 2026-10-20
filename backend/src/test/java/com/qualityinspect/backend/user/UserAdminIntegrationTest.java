package com.qualityinspect.backend.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * User-administration API tests: QUALITY_MANAGER-only create/enable/disable/roles/password.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAdminIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    String inspectorToken;
    String managerToken;
    long managerId;

    @BeforeEach
    void seedUsers() {
        RoleEntity inspectorRole = roleRepository.findByCode("INSPECTOR").orElseThrow();
        RoleEntity managerRole = roleRepository.findByCode("QUALITY_MANAGER").orElseThrow();

        UserEntity inspector = upsert("ua_inspector", "Inspector!123", inspectorRole);
        UserEntity manager = upsert("ua_manager", "Manager!12345", managerRole);
        managerId = manager.getId();

        inspectorToken = login("ua_inspector", "Inspector!123", 200);
        managerToken = login("ua_manager", "Manager!12345", 200);
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

    private String login(String username, String password, int expectedStatus) {
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().is(expectedStatus))
                    .andReturn();
            if (expectedStatus != 200) return null;
            return objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- RBAC ----

    @Test
    void inspector_cannotListAdminUsers_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin")
                        .header("Authorization", "Bearer " + inspectorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void inspector_cannotCreateUser_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + inspectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new_one\",\"password\":\"Password1!\",\"roles\":[\"INSPECTOR\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_adminList_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin"))
                .andExpect(status().isUnauthorized());
    }

    // ---- create ----

    @Test
    void manager_createsUser_returns201_andNewUserCanLogin() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ua_created\",\"password\":\"Password1!\","
                                + "\"displayName\":\"Created User\",\"roles\":[\"INSPECTOR\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ua_created"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roles[0]").value("INSPECTOR"));

        login("ua_created", "Password1!", 200);
    }

    @Test
    void manager_createDuplicateUsername_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ua_inspector\",\"password\":\"Password1!\",\"roles\":[\"INSPECTOR\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_withUnknownRole_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ua_badrole\",\"password\":\"Password1!\",\"roles\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withShortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ua_short\",\"password\":\"short\",\"roles\":[\"INSPECTOR\"]}"))
                .andExpect(status().isBadRequest());
    }

    // ---- enable / disable ----

    @Test
    void disable_thenLogin_fails_thenReenable_loginWorks() throws Exception {
        UserEntity target = upsert("ua_toggle", "Toggle!12345",
                roleRepository.findByCode("INSPECTOR").orElseThrow());
        login("ua_toggle", "Toggle!12345", 200);

        mockMvc.perform(patch("/api/v1/users/" + target.getId() + "/enabled")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        login("ua_toggle", "Toggle!12345", 401);

        mockMvc.perform(patch("/api/v1/users/" + target.getId() + "/enabled")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        login("ua_toggle", "Toggle!12345", 200);
    }

    @Test
    void manager_cannotDisableOwnAccount_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + managerId + "/enabled")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    // ---- roles ----

    @Test
    void manager_updatesRoles_returns200() throws Exception {
        UserEntity target = upsert("ua_roles", "Roles!12345",
                roleRepository.findByCode("INSPECTOR").orElseThrow());

        mockMvc.perform(put("/api/v1/users/" + target.getId() + "/roles")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"SUPERVISOR\",\"INSPECTOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
    }

    // ---- password reset ----

    @Test
    void manager_resetsPassword_oldFails_newWorks() throws Exception {
        UserEntity target = upsert("ua_reset", "OldPass!1234",
                roleRepository.findByCode("INSPECTOR").orElseThrow());
        login("ua_reset", "OldPass!1234", 200);

        mockMvc.perform(put("/api/v1/users/" + target.getId() + "/password")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPass!1234\"}"))
                .andExpect(status().isNoContent());

        login("ua_reset", "OldPass!1234", 401);
        login("ua_reset", "NewPass!1234", 200);
    }
}
