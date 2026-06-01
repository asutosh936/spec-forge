package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.model.GeneratedFile;
import com.example.apicodegen.model.ReviewReport;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class ReviewerAgent extends AgentService {

    public ReviewerAgent(RestClient anthropicRestClient, AnthropicProperties props) {
        super(anthropicRestClient, props);
    }

    public ReviewReport review(List<GeneratedFile> allFiles) {
        // TODO: implement in Phase 2
        throw new UnsupportedOperationException("ReviewerAgent not yet implemented");
    }
}
