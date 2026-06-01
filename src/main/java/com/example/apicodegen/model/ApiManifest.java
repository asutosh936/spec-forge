package com.example.apicodegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiManifest(
        String title,
        String version,
        String description,
        String basePackage,
        List<EndpointInfo> endpoints,
        List<SchemaInfo> schemas,
        int endpointCount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EndpointInfo(
            String method,
            String path,
            String operationId,
            String summary,
            String requestBody,
            String responseSchema,
            List<String> pathParams,
            List<String> queryParams,
            List<String> tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SchemaInfo(
            String name,
            List<FieldInfo> fields
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldInfo(
            String name,
            String type,
            boolean required
    ) {}
}
