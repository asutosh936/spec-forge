package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.ApiManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class SpecAnalystAgent extends AgentService {

    private static final Logger log = LoggerFactory.getLogger(SpecAnalystAgent.class);

    @Value("classpath:prompts/analyst-system.txt")
    private Resource systemPromptResource;

    public SpecAnalystAgent(RestClient anthropicRestClient, AnthropicProperties props) {
        super(anthropicRestClient, props);
    }

    public ApiManifest analyze(String specText) {
        log.info("SpecAnalystAgent: starting analysis of {} character spec", specText.length());
        String systemPrompt = loadSystemPrompt();
        var request = buildRequest(systemPrompt, specText);
        String responseText = callClaude(request);
        ApiManifest manifest = parseJson(responseText, ApiManifest.class);
        log.info("SpecAnalystAgent: analysis complete - {} endpoints extracted", manifest.endpointCount());
        return manifest;
    }

    private String loadSystemPrompt() {
        try {
            String prompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded analyst system prompt: {} bytes", prompt.length());
            return prompt;
        } catch (IOException e) {
            log.error("Failed to load analyst system prompt", e);
            throw new RuntimeException("Failed to load analyst system prompt", e);
        }
    }
}
