package com.example.apicodegen.llm;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Test-only LlmClient — reads a canned response from fixtures.
 * Zero network calls. Zero API cost. Activated by the "test" Spring profile.
 */
@Component
@Profile("test")
public class StubLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(StubLlmClient.class);

    @Value("classpath:fixtures/sample-claude-response.json")
    private Resource fixture;

    @PostConstruct
    void logActivation() {
        log.info("=======================================================");
        log.info("[STUB] LLM mode: OFFLINE — using fixture response");
        log.info("[STUB] No real API calls will be made. Zero cost.");
        log.info("[STUB] Fixture: classpath:fixtures/sample-claude-response.json");
        log.info("=======================================================");
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        log.info("[STUB] Returning canned fixture response (no API call)");
        try {
            return fixture.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read test fixture: " + e.getMessage(), e);
        }
    }
}
