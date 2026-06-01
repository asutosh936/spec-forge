package com.example.apicodegen.llm;

public interface LlmClient {

    /**
     * Send a single-turn completion request.
     *
     * @param systemPrompt instructions for the model
     * @param userMessage  the user content / spec text
     * @return the model's text response
     */
    String complete(String systemPrompt, String userMessage);
}
