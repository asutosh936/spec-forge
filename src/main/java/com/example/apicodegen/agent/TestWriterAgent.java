package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GeneratedFile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class TestWriterAgent extends AgentService {

    public TestWriterAgent(RestClient anthropicRestClient, AnthropicProperties props) {
        super(anthropicRestClient, props);
    }

    public List<GeneratedFile> writeTests(List<GeneratedFile> sourceFiles, ApiManifest manifest) {
        // TODO: implement in Phase 2
        throw new UnsupportedOperationException("TestWriterAgent not yet implemented");
    }
}
