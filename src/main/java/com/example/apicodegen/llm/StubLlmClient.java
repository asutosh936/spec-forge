package com.example.apicodegen.llm;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Offline LLM stub — returns a canned fixture response with zero API calls.
 *
 * Activated by setting  llm.stub=true  in any property source:
 *   mvn spring-boot:run -Dllm.stub=true
 *   SPRING_PROFILES_ACTIVE=test  (loads application-test.yml which sets llm.stub=true)
 */
@Component
@ConditionalOnProperty(name = "llm.stub", havingValue = "true")
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
