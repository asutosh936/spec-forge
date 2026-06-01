package com.example.apicodegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewReport(
        String overallScore,
        List<Issue> issues,
        List<String> suggestions,
        String summary
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(
            String severity,
            String file,
            Integer line,
            String message
    ) {}
}
