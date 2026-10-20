package com.qualityinspect.backend.user;

import com.qualityinspect.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/demo-only seeder: creates INSPECTOR and SUPERVISOR accounts alongside the
 * single-use bootstrap quality manager so the three-role workflow can be
 * demonstrated. Runs only when app.bootstrap.enabled=true and is idempotent
 * (existing usernames are never touched).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!appProperties.getBootstrap().isEnabled()) {
            return;
        }
        seed("inspector", "Demo Inspector", "INSPECTOR",
                envOr("APP_DEMO_INSPECTOR_PASSWORD", "ChangeMe!in1"));
        seed("supervisor", "Demo Supervisor", "SUPERVISOR",
                envOr("APP_DEMO_SUPERVISOR_PASSWORD", "ChangeMe!su1"));
    }

    private void seed(String username, String displayName, String roleCode, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        RoleEntity role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException(roleCode + " role missing"));
        UserEntity user = new UserEntity();
        user.setOrgId("default");
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setEnabled(true);
        user.getRoles().add(role);
        userRepository.save(user);
        log.info("Seeded demo user '{}' with role {}", username, roleCode);
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
