package com.qualityinspect.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualityinspect.backend.common.error.ApiException.ServiceUnavailableException;
import com.qualityinspect.backend.config.GenAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin client for any OpenAI-compatible chat-completions provider.
 * The provider is swappable purely through configuration; the API key never
 * leaves the backend.
 */
@Slf4j
@Component
public class GenAiClient {

    private final GenAiProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GenAiClient(GenAiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds() / 3)));
                    setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
                }})
                .build();
    }

    public boolean enabled() {
        return props.enabled();
    }

    public record LlmAnswer(String responseText, List<String> suggestions, List<String> missingEvidence) {}

    public LlmAnswer chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "temperature", props.getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        String raw;
        try {
            raw = restClient.post()
                    .uri(trimSlash(props.getBaseUrl()) + "/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new ServiceUnavailableException("Generative AI provider is unavailable: " + safe(ex));
        }
        if (raw == null || raw.isBlank()) {
            throw new ServiceUnavailableException("Generative AI provider returned an empty response");
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new ServiceUnavailableException("Generative AI response contained no message content");
            }
            return parseContent(content);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Could not parse Generative AI response: " + safe(ex));
        }
    }

    /** Tolerant parser: accepts fenced or bare JSON; falls back to plain text. */
    private LlmAnswer parseContent(String content) {
        String candidate = content.trim();
        if (candidate.startsWith("```")) {
            candidate = candidate.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        try {
            JsonNode node = objectMapper.readTree(candidate);
            String text = node.path("responseText").asText("").trim();
            if (text.isEmpty()) {
                text = node.path("answer").asText("").trim();
            }
            List<String> suggestions = toStringList(node.path("suggestions"));
            List<String> missing = toStringList(node.path("missingEvidence"));
            if (text.isEmpty()) {
                return new LlmAnswer(content.trim(), List.of(), List.of());
            }
            return new LlmAnswer(text, suggestions, missing);
        } catch (Exception notJson) {
            return new LlmAnswer(content.trim(), List.of(), List.of());
        }
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(item -> {
                String value = item.asText("");
                if (!value.isBlank()) {
                    list.add(value.trim());
                }
            });
        }
        return list;
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String safe(Exception ex) {
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String cleaned = msg.replaceAll("(?i)(api[_-]?key|authorization|token)=[^&\\s]+", "$1=***");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }
}
