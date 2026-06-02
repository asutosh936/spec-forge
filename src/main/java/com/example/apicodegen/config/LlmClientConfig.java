package com.example.apicodegen.config;

import com.example.apicodegen.llm.AnthropicLlmClient;
import com.example.apicodegen.llm.LlmClient;
import com.example.apicodegen.llm.OpenAiCompatibleLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfig.class);

    /**
     * Creates the real LLM client ONLY if no LlmClient bean already exists.
     * StubLlmClient (@Component @Profile("test")) is registered during component
     * scanning — before @Configuration bean methods run — so this condition
     * reliably detects it and skips the real client in test mode.
     */
    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    public LlmClient llmClient(LlmProperties props) {
        String provider = props.getProvider().toLowerCase();

        log.info("=======================================================");
        log.info("[REAL] LLM mode: LIVE — API calls will be made");
        log.info("[REAL] provider={}, model={}", provider, props.getModel());
        log.info("[REAL] baseUrl={}", props.getBaseUrl());
        log.info("=======================================================");

        RestClient restClient = buildRestClient(props);

        if ("anthropic".equals(provider)) {
            return new AnthropicLlmClient(restClient, props.getModel(), props.getMaxTokens());
        } else {
            return new OpenAiCompatibleLlmClient(restClient, props.getModel(), props.getMaxTokens());
        }
    }

    private RestClient buildRestClient(LlmProperties props) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("content-type", "application/json");

        String provider = props.getProvider().toLowerCase();
        String apiKey   = props.getApiKey();

        if ("anthropic".equals(provider)) {
            builder.defaultHeader("x-api-key", apiKey)
                   .defaultHeader("anthropic-version", "2023-06-01");
        } else if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        return builder.build();
    }
}
