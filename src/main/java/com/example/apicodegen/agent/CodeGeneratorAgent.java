package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GeneratedFile;
import com.example.apicodegen.model.ProjectBlueprint;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class CodeGeneratorAgent extends AgentService {

    @SuppressWarnings("unused")
    private final ExecutorService agentExecutor;

    public CodeGeneratorAgent(RestClient anthropicRestClient, AnthropicProperties props,
                               ExecutorService agentExecutor) {
        super(anthropicRestClient, props);
        this.agentExecutor = agentExecutor;
    }

    public List<GeneratedFile> generate(ProjectBlueprint blueprint, ApiManifest manifest) {
        // TODO: implement in Phase 2
        throw new UnsupportedOperationException("CodeGeneratorAgent not yet implemented");
    }
}
