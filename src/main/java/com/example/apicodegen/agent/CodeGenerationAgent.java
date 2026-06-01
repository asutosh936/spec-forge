package com.example.apicodegen.agent;

import com.example.apicodegen.llm.LlmClient;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GeneratedFile;
import com.example.apicodegen.model.ProjectBlueprint;
import com.example.apicodegen.prompt.PromptBuilder;
import com.example.apicodegen.response.GenerationResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The only class that calls the LLM. Makes exactly one call per generation run.
 */
@Service
public class CodeGenerationAgent {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationAgent.class);

    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final GenerationResponseParser responseParser;

    public CodeGenerationAgent(LlmClient llmClient, PromptBuilder promptBuilder,
                                GenerationResponseParser responseParser) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    public List<GeneratedFile> generate(ApiManifest manifest, ProjectBlueprint blueprint,
                                         String userContext) {
        log.info("CodeGenerationAgent: generating {} files in a single LLM call",
                blueprint.filePlan().size());

        String systemPrompt = promptBuilder.getSystemPrompt();
        String userMessage  = promptBuilder.buildUserMessage(manifest, blueprint, userContext);
        String rawResponse  = llmClient.complete(systemPrompt, userMessage);

        List<GeneratedFile> files = responseParser.parse(rawResponse);
        log.info("CodeGenerationAgent: received {} files from LLM", files.size());
        return files;
    }
}
