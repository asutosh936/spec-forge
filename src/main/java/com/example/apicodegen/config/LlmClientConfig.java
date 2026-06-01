package com.example.apicodegen.config;

import com.example.apicodegen.llm.AnthropicLlmClient;
import com.example.apicodegen.llm.LlmClient;
import com.example.apicodegen.llm.OpenAiCompatibleLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("!test")
public class LlmClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfig.class);

    @Bean
    public LlmClient llmClient(LlmProperties props) {
        RestClient restClient = buildRestClient(props);

        String provider = props.getProvider().toLowerCase();
        log.info("=======================================================");
        log.info("[REAL] LLM mode: LIVE — API calls will be made");
        log.info("[REAL] provider={}, model={}", provider, props.getModel());
        log.info("[REAL] baseUrl={}", props.getBaseUrl());
        log.info("=======================================================");

        if ("anthropic".equals(provider)) {
            return new AnthropicLlmClient(restClient, props.getModel(), props.getMaxTokens());
        } else {
            // ollama, groq, openai — all use the OpenAI-compatible /chat/completions format
            return new OpenAiCompatibleLlmClient(restClient, props.getModel(), props.getMaxTokens());
        }
    }

    private RestClient buildRestClient(LlmProperties props) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("content-type", "application/json");

        String provider = props.getProvider().toLowerCase();
        String apiKey = props.getApiKey();

        if ("anthropic".equals(provider)) {
            builder.defaultHeader("x-api-key", apiKey)
                   .defaultHeader("anthropic-version", "2023-06-01");
        } else if (apiKey != null && !apiKey.isBlank()) {
            // Groq, OpenAI, and Ollama (optional) use Bearer token
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        return builder.build();
    }
}
