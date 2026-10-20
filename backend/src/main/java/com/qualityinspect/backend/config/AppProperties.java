package com.qualityinspect.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Typed application configuration bound from `app.*` in application.yml / environment. */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Bootstrap bootstrap = new Bootstrap();
    private Storage storage = new Storage();
    private Ml ml = new Ml();
    private Cors cors = new Cors();
    private Registration registration = new Registration();

    @Data
    public static class Jwt {
        private String secret;
        private long accessMinutes = 30;
        private long refreshDays = 7;
    }

    @Data
    public static class Bootstrap {
        private boolean enabled;
        private String username;
        private String password;
        private String org;
    }

    @Data
    public static class Storage {
        private String dir = "./storage";
        private long maxImageBytes = 10L * 1024 * 1024;
    }

    @Data
    public static class Ml {
        private String url = "http://localhost:8000";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    @Data
    public static class Registration {
        private long otpTtlMinutes = 5;
        private int maxAttempts = 5;
        private boolean devOtpInResponse = true;
        private boolean emailEnabled = true;
        private String mailFrom = "teamdefectra@gmail.com";
        private String mailFromName = "Defectra AI Support Team";
        private String supportEmail = "teamdefectra@gmail.com";
    }
}
