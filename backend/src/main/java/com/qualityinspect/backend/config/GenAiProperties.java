package com.qualityinspect.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** GenAI provider configuration — server side only, never exposed to the APK. */
@Data
@ConfigurationProperties(prefix = "genai")
public class GenAiProperties {

    /** `openai-compatible` enables real LLM calls; `none` returns a labelled dev evidence summary. */
    private String provider = "none";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private double temperature = 0.1;
    private int timeoutSeconds = 45;

    public boolean enabled() {
        return provider != null && !provider.isBlank()
                && !"none".equalsIgnoreCase(provider)
                && apiKey != null && !apiKey.isBlank();
    }
}
