package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.exception.AgentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public abstract class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    protected final RestClient anthropicClient;
    protected final AnthropicProperties props;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected AgentService(RestClient anthropicClient, AnthropicProperties props) {
        this.anthropicClient = anthropicClient;
        this.props = props;
    }

    protected Map<String, Object> buildRequest(String systemPrompt, String userMessage) {
        log.debug("Building Claude request for model {} with max_tokens {}", props.getModel(), props.getMaxTokens());
        Map<String, Object> request = Map.of(
            "model", props.getModel(),
            "max_tokens", props.getMaxTokens(),
            "system", systemPrompt,
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            )
        );
        log.debug("Request built: {} bytes of message content", userMessage.length());
        return request;
    }

    protected String callClaude(Map<String, Object> requestBody) {
        try {
            log.info("Calling Claude API at {}/messages", props.getBaseUrl());
            String responseBody = anthropicClient.post()
                    .uri("/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.debug("Received response from Claude: {} bytes", responseBody.length());

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (content.isEmpty()) {
                log.error("Claude returned empty content in response");
                throw new AgentException("Claude returned an empty response");
            }
            String text = content.get(0).path("text").asText();
            log.info("Claude response received: {} characters of content", text.length());
            return text;
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            throw new AgentException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    protected <T> T parseJson(String text, Class<T> type) {
        log.debug("Parsing Claude response as {}", type.getSimpleName());

        // Strip markdown code fences if Claude included them
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            log.debug("Stripping markdown code fences from response");
            cleaned = cleaned.replaceFirst("```[a-zA-Z]*\\n?", "");
            int closingFence = cleaned.lastIndexOf("```");
            if (closingFence >= 0) {
                cleaned = cleaned.substring(0, closingFence);
            }
            cleaned = cleaned.trim();
        }

        try {
            T result = objectMapper.readValue(cleaned, type);
            log.info("Successfully parsed response as {}", type.getSimpleName());
            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse response as {}: {}", type.getSimpleName(), e.getMessage());
            log.debug("Response text (first 500 chars): {}", cleaned.substring(0, Math.min(500, cleaned.length())));
            throw new AgentException("Failed to parse agent response as " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
