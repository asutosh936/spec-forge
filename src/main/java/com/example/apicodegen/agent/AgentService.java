package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.exception.AgentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public abstract class AgentService {

    protected final RestClient anthropicClient;
    protected final AnthropicProperties props;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected AgentService(RestClient anthropicClient, AnthropicProperties props) {
        this.anthropicClient = anthropicClient;
        this.props = props;
    }

    protected Map<String, Object> buildRequest(String systemPrompt, String userMessage) {
        return Map.of(
            "model", props.getModel(),
            "max_tokens", props.getMaxTokens(),
            "system", systemPrompt,
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            )
        );
    }

    protected String callClaude(Map<String, Object> requestBody) {
        try {
            String responseBody = anthropicClient.post()
                    .uri("/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (content.isEmpty()) {
                throw new AgentException("Claude returned an empty response");
            }
            return content.get(0).path("text").asText();
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    protected <T> T parseJson(String text, Class<T> type) {
        // Strip markdown code fences if Claude included them
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("```[a-zA-Z]*\\n?", "");
            int closingFence = cleaned.lastIndexOf("```");
            if (closingFence >= 0) {
                cleaned = cleaned.substring(0, closingFence);
            }
            cleaned = cleaned.trim();
        }
        try {
            return objectMapper.readValue(cleaned, type);
        } catch (JsonProcessingException e) {
            throw new AgentException("Failed to parse agent response as " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
