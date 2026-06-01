package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.ApiManifest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class SpecAnalystAgent extends AgentService {

    @Value("classpath:prompts/analyst-system.txt")
    private Resource systemPromptResource;

    public SpecAnalystAgent(RestClient anthropicRestClient, AnthropicProperties props) {
        super(anthropicRestClient, props);
    }

    public ApiManifest analyze(String specText) {
        String systemPrompt = loadSystemPrompt();
        var request = buildRequest(systemPrompt, specText);
        String responseText = callClaude(request);
        return parseJson(responseText, ApiManifest.class);
    }

    private String loadSystemPrompt() {
        try {
            return systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load analyst system prompt", e);
        }
    }
}
