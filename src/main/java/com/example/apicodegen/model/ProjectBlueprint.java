package com.example.apicodegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectBlueprint(
        String projectName,
        Language language,
        String packageRoot,
        List<FilePlan> filePlan
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilePlan(
            String path,
            String fileType,
            String description
    ) {}
}
