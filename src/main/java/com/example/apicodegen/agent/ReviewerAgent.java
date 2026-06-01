package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.GeneratedFile;
import com.example.apicodegen.model.ReviewReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ReviewerAgent extends AgentService {

    private static final Logger log = LoggerFactory.getLogger(ReviewerAgent.class);

    @Value("classpath:prompts/reviewer-system.txt")
    private Resource systemPromptResource;

    public ReviewerAgent(RestClient anthropicRestClient, AnthropicProperties props) {
        super(anthropicRestClient, props);
    }

    public ReviewReport review(List<GeneratedFile> allFiles) {
        log.info("ReviewerAgent: reviewing {} generated files", allFiles.size());
        String systemPrompt = loadSystemPrompt();

        StringBuilder filesContent = new StringBuilder();
        for (GeneratedFile file : allFiles) {
            filesContent.append("\n\n=== ").append(file.filename()).append(" (").append(file.fileType()).append(") ===\n");
            filesContent.append(file.content());
        }

        String userMessage = "Review the following generated source code files for issues, best practices, and quality:\n" +
                             filesContent.toString();

        var request = buildRequest(systemPrompt, userMessage);
        String responseText = callClaude(request);
        ReviewReport report = parseJson(responseText, ReviewReport.class);

        log.info("ReviewerAgent: review complete - overall score: {}", report.overallScore());
        return report;
    }

    private String loadSystemPrompt() {
        try {
            String prompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded reviewer system prompt: {} bytes", prompt.length());
            return prompt;
        } catch (IOException e) {
            log.error("Failed to load reviewer system prompt", e);
            throw new RuntimeException("Failed to load reviewer system prompt", e);
        }
    }
}
