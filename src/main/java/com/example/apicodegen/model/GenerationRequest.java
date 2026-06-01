package com.example.apicodegen.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class GenerationRequest {

    @NotBlank(message = "Spec is required")
    private String spec;

    @NotNull(message = "Language is required")
    private Language language;

    private String additionalContext;

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }

    public String getAdditionalContext() { return additionalContext; }
    public void setAdditionalContext(String additionalContext) { this.additionalContext = additionalContext; }
}
