package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.Language;
import com.example.apicodegen.model.ProjectBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class ArchitectAgent extends AgentService {

    private static final Logger log = LoggerFactory.getLogger(ArchitectAgent.class);

    @Value("classpath:prompts/architect-system.txt")
    private Resource systemPromptResource;

    public ArchitectAgent(RestClient anthropicRestClient, AnthropicProperties props) {
        super(anthropicRestClient, props);
    }

    public ProjectBlueprint design(ApiManifest manifest, Language language) {
        log.info("ArchitectAgent: designing project blueprint for {} endpoints ({})", manifest.endpointCount(), language);
        String systemPrompt = loadSystemPrompt();
        String userMessage = objectMapper.valueToTree(manifest).toString() +
                            "\n\nTarget language: " + language.name();
        var request = buildRequest(systemPrompt, userMessage);
        String responseText = callClaude(request);
        ProjectBlueprint blueprint = parseJson(responseText, ProjectBlueprint.class);
        log.info("ArchitectAgent: blueprint designed with {} files", blueprint.filePlan().size());
        return blueprint;
    }

    private String loadSystemPrompt() {
        try {
            String prompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded architect system prompt: {} bytes", prompt.length());
            return prompt;
        } catch (IOException e) {
            log.error("Failed to load architect system prompt", e);
            throw new RuntimeException("Failed to load architect system prompt", e);
        }
    }
}
