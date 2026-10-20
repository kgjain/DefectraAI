package com.qualityinspect.backend.health;

import com.qualityinspect.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.TreeMap;

/** Public health endpoint: database, private storage and ML service reachability. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final AppProperties appProperties;
    private final RestClient restClient = RestClient.create();

    public record HealthResponse(String status, Map<String, String> components) {}

    @GetMapping("/api/v1/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, String> components = new TreeMap<>();

        components.put("database", checkDatabase());
        components.put("storage", checkStorage());
        components.put("mlService", checkMl());

        boolean up = "UP".equals(components.get("database"));
        return ResponseEntity.ok(new HealthResponse(up ? "UP" : "DOWN", components));
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }

    private String checkStorage() {
        try {
            Path dir = Path.of(appProperties.getStorage().getDir());
            return Files.isDirectory(dir) && Files.isWritable(dir) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }

    private String checkMl() {
        try {
            String body = restClient.get()
                    .uri(appProperties.getMl().getUrl() + "/health")
                    .retrieve()
                    .body(String.class);
            return body != null ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "UNREACHABLE";
        }
    }
}
