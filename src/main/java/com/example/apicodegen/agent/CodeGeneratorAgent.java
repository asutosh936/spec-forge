package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.exception.AgentException;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GeneratedFile;
import com.example.apicodegen.model.ProjectBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
public class CodeGeneratorAgent extends AgentService {

    private static final Logger log = LoggerFactory.getLogger(CodeGeneratorAgent.class);

    @SuppressWarnings("unused")
    private final ExecutorService agentExecutor;

    @Value("classpath:prompts/codegen-system.txt")
    private Resource systemPromptResource;

    public CodeGeneratorAgent(RestClient anthropicRestClient, AnthropicProperties props,
                               ExecutorService agentExecutor) {
        super(anthropicRestClient, props);
        this.agentExecutor = agentExecutor;
    }

    public List<GeneratedFile> generate(ProjectBlueprint blueprint, ApiManifest manifest) {
        log.info("CodeGeneratorAgent: generating {} files in parallel", blueprint.filePlan().size());
        String systemPrompt = loadSystemPrompt();

        List<Future<GeneratedFile>> futures = new ArrayList<>();
        for (var filePlan : blueprint.filePlan()) {
            Future<GeneratedFile> future = agentExecutor.submit(() ->
                generateFile(filePlan, blueprint, manifest, systemPrompt)
            );
            futures.add(future);
        }

        List<GeneratedFile> results = new ArrayList<>();
        for (Future<GeneratedFile> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.error("Failed to generate file: {}", e.getMessage(), e);
                throw new AgentException("Code generation failed: " + e.getMessage(), e);
            }
        }

        log.info("CodeGeneratorAgent: {} files generated successfully", results.size());
        return results;
    }

    private GeneratedFile generateFile(ProjectBlueprint.FilePlan filePlan, ProjectBlueprint blueprint,
                                      ApiManifest manifest, String systemPrompt) {
        log.debug("Generating file: {}", filePlan.filename());

        String userMessage = String.format(
            "Generate the file: %s\n\nFile type: %s\nPurpose: %s\nDependencies: %s\n\nProject blueprint:\n%s\n\nAPI manifest:\n%s",
            filePlan.filename(), filePlan.fileType(), filePlan.purpose(), filePlan.dependsOn(),
            objectMapper.valueToTree(blueprint).toString(),
            objectMapper.valueToTree(manifest).toString()
        );

        var request = buildRequest(systemPrompt, userMessage);
        String responseText = callClaude(request);

        log.debug("File generated: {} ({} bytes)", filePlan.filename(), responseText.length());
        return new GeneratedFile(filePlan.filename(), responseText, filePlan.fileType());
    }

    private String loadSystemPrompt() {
        try {
            String prompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded codegen system prompt: {} bytes", prompt.length());
            return prompt;
        } catch (IOException e) {
            log.error("Failed to load codegen system prompt", e);
            throw new RuntimeException("Failed to load codegen system prompt", e);
        }
    }
}
