package com.example.apicodegen.agent;

import com.example.apicodegen.config.AnthropicProperties;
import com.example.apicodegen.exception.AgentException;
import com.example.apicodegen.model.ApiManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SpecAnalystAgentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestableSpecAnalystAgent agent;

    @BeforeEach
    void setUp() {
        AnthropicProperties props = new AnthropicProperties();
        props.setApiKey("test-key");
        props.setModel("claude-haiku-4-5-20251001");
        props.setMaxTokens(4096);
        agent = new TestableSpecAnalystAgent(props);
    }

    @Test
    void parsesValidApiManifestResponse() throws Exception {
        ApiManifest expected = new ApiManifest(
                "Pet Store", "1.0", "A pet store API", "com.example.petstore",
                List.of(new ApiManifest.EndpointInfo("GET", "/pets", "listPets", "List pets",
                        null, "Pet[]", List.of(), List.of("limit"), List.of("pets"))),
                List.of(new ApiManifest.SchemaInfo("Pet", List.of(
                        new ApiManifest.FieldInfo("id", "integer", true),
                        new ApiManifest.FieldInfo("name", "string", true)
                ))),
                1
        );

        agent.setCannedClaudeResponse(MAPPER.writeValueAsString(expected));

        ApiManifest result = agent.analyze("openapi: 3.0.0\n...");

        assertThat(result.title()).isEqualTo("Pet Store");
        assertThat(result.endpointCount()).isEqualTo(1);
        assertThat(result.endpoints()).hasSize(1);
        assertThat(result.endpoints().get(0).method()).isEqualTo("GET");
        assertThat(result.schemas()).hasSize(1);
    }

    @Test
    void stripsMarkdownFencesFromResponse() throws Exception {
        ApiManifest manifest = new ApiManifest(
                "Test API", "1.0", "Test", "com.example.test",
                List.of(), List.of(), 0
        );
        agent.setCannedClaudeResponse("```json\n" + MAPPER.writeValueAsString(manifest) + "\n```");

        ApiManifest result = agent.analyze("spec text");

        assertThat(result.title()).isEqualTo("Test API");
    }

    @Test
    void throwsAgentExceptionOnInvalidJson() {
        agent.setCannedClaudeResponse("not valid json at all");

        assertThatThrownBy(() -> agent.analyze("spec text"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("ApiManifest");
    }

    @Test
    void buildRequestIncludesSystemPromptAndSpec() {
        agent.setCannedClaudeResponse("{}");
        agent.captureLastRequest = true;

        try { agent.analyze("my spec"); } catch (Exception ignored) {}

        assertThat(agent.lastRequest).containsKey("system");
        assertThat(agent.lastRequest).containsKey("messages");
        assertThat(agent.lastRequest).containsKey("model");
    }

    // Test double: overrides callClaude to return a canned response,
    // and overrides analyze to skip classpath prompt loading.
    private static class TestableSpecAnalystAgent extends SpecAnalystAgent {

        private String cannedResponse = "{}";
        Map<String, Object> lastRequest;
        boolean captureLastRequest = false;

        TestableSpecAnalystAgent(AnthropicProperties props) {
            super(null, props);
        }

        void setCannedClaudeResponse(String response) {
            this.cannedResponse = response;
        }

        @Override
        protected String callClaude(Map<String, Object> requestBody) {
            if (captureLastRequest) {
                this.lastRequest = requestBody;
            }
            return cannedResponse;
        }

        @Override
        public ApiManifest analyze(String specText) {
            String systemPrompt = "You are a spec analyst. Return JSON only.";
            var request = buildRequest(systemPrompt, specText);
            String responseText = callClaude(request);
            return parseJson(responseText, ApiManifest.class);
        }
    }
}
