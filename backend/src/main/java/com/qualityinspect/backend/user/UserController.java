package com.qualityinspect.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/** Read-only user directory for owner/reviewer pickers in the mobile UI. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    public record UserOption(long id, String username, String displayName, Set<String> roles) {}

    @GetMapping
    public List<UserOption> list(@RequestParam(required = false) String role) {
        return userRepository.findAllWithRoles().stream()
                .filter(UserEntity::isEnabled)
                .filter(u -> role == null || role.isBlank()
                        || u.roleCodes().stream().anyMatch(r -> r.equalsIgnoreCase(role.trim())))
                .map(u -> new UserOption(u.getId(), u.getUsername(), u.getDisplayName(), u.roleCodes()))
                .toList();
    }
}
