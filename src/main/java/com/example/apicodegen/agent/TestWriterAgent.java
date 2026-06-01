package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.exception.AgentException;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GeneratedFile;
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
public class TestWriterAgent extends AgentService {

    private static final Logger log = LoggerFactory.getLogger(TestWriterAgent.class);

    @SuppressWarnings("unused")
    private final ExecutorService agentExecutor;

    @Value("classpath:prompts/testwriter-system.txt")
    private Resource systemPromptResource;

    public TestWriterAgent(RestClient anthropicRestClient, AnthropicProperties props,
                           ExecutorService agentExecutor) {
        super(anthropicRestClient, props);
        this.agentExecutor = agentExecutor;
    }

    public List<GeneratedFile> writeTests(List<GeneratedFile> sourceFiles, ApiManifest manifest) {
        log.info("TestWriterAgent: generating tests for {} source files in parallel", sourceFiles.size());
        String systemPrompt = loadSystemPrompt();

        List<Future<GeneratedFile>> futures = new ArrayList<>();
        for (var sourceFile : sourceFiles) {
            Future<GeneratedFile> future = agentExecutor.submit(() ->
                generateTestForFile(sourceFile, manifest, systemPrompt)
            );
            futures.add(future);
        }

        List<GeneratedFile> testFiles = new ArrayList<>();
        for (Future<GeneratedFile> future : futures) {
            try {
                testFiles.add(future.get());
            } catch (Exception e) {
                log.error("Failed to generate test: {}", e.getMessage(), e);
                throw new AgentException("Test generation failed: " + e.getMessage(), e);
            }
        }

        log.info("TestWriterAgent: {} test files generated successfully", testFiles.size());
        return testFiles;
    }

    private GeneratedFile generateTestForFile(GeneratedFile sourceFile, ApiManifest manifest, String systemPrompt) {
        log.debug("Generating tests for: {}", sourceFile.filename());

        String userMessage = String.format(
            "Write comprehensive tests for this source file:\n\nFilename: %s\nContent:\n%s\n\nAPI manifest:\n%s",
            sourceFile.filename(), sourceFile.content(),
            objectMapper.valueToTree(manifest).toString()
        );

        var request = buildRequest(systemPrompt, userMessage);
        String responseText = callClaude(request);

        String testFilename = deriveTestFilename(sourceFile.filename());
        log.debug("Test file generated: {} ({} bytes)", testFilename, responseText.length());
        return new GeneratedFile(testFilename, responseText, "TEST");
    }

    private String deriveTestFilename(String sourceFilename) {
        // Convert src/main/java/.../MyClass.java → src/test/java/.../MyClassTest.java
        if (sourceFilename.contains("src/main")) {
            return sourceFilename
                .replace("src/main", "src/test")
                .replace(".java", "Test.java")
                .replace(".py", "_test.py");
        }
        return sourceFilename + ".test";
    }

    private String loadSystemPrompt() {
        try {
            String prompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded testwriter system prompt: {} bytes", prompt.length());
            return prompt;
        } catch (IOException e) {
            log.error("Failed to load testwriter system prompt", e);
            throw new RuntimeException("Failed to load testwriter system prompt", e);
        }
    }
}
