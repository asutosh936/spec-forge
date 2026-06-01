package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.Language;
import com.example.apicodegen.model.ProjectBlueprint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class ArchitectAgent extends AgentService {

    @Value("classpath:prompts/architect-system.txt")
    private Resource systemPromptResource;

    public ArchitectAgent(RestClient anthropicRestClient, AnthropicProperties props) {
        super(anthropicRestClient, props);
    }

    public ProjectBlueprint design(ApiManifest manifest, Language language) {
        // TODO: implement in Phase 2
        throw new UnsupportedOperationException("ArchitectAgent not yet implemented");
    }

    private String loadSystemPrompt() {
        try {
            return systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load architect system prompt", e);
        }
    }
}
