package com.example.apicodegen.llm;

import com.example.apicodegen.exception.AgentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls any OpenAI-compatible API (Ollama, Groq, OpenAI).
 * Request:  { model, max_tokens, messages: [{role:system}, {role:user}] }
 * Response: choices[0].message.content
 */
@Profile("!test")
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final int MAX_RETRIES = 4;
    private static final long BASE_DELAY_MS = 2_000L;

    private final RestClient restClient;
    private final String model;
    private final int maxTokens;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleLlmClient(RestClient restClient, String model, int maxTokens) {
        this.restClient = restClient;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        int attempt = 0;
        while (true) {
            try {
                return doComplete(systemPrompt, userMessage);
            } catch (AgentException e) {
                boolean retryable = e.getCause() instanceof HttpClientErrorException.TooManyRequests
                        || e.getCause() instanceof HttpServerErrorException;
                if (!retryable || ++attempt > MAX_RETRIES) throw e;

                long waitMs = BASE_DELAY_MS * (1L << attempt);
                log.warn("LLM API error (attempt {}/{}), retrying in {}ms: {}",
                        attempt, MAX_RETRIES, waitMs, e.getMessage());
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private String doComplete(String systemPrompt, String userMessage) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                )
        );
        try {
            log.info("[REAL LLM] Calling OpenAI-compatible API — model={}", model);
            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                throw new AgentException("LLM returned an empty choices array");
            }
            return choices.get(0).path("message").path("content").asText();
        } catch (AgentException e) {
            throw e;
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AgentException("Rate limit exceeded: " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            throw new AgentException("LLM API client error: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            throw new AgentException("LLM API server error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AgentException("LLM API call failed: " + e.getMessage(), e);
        }
    }
}
