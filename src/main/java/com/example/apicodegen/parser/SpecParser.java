package com.example.apicodegen.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

@Component
public class SpecParser {

    private static final int MAX_ENDPOINTS = 10;

    @Value("${codegen.spec.max-size-kb:128}")
    private int maxSizeKb = 128;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> parse(String input) {
        if (input == null || input.isBlank()) {
            throw new SpecValidationException("Spec must not be empty");
        }
        if (input.length() > maxSizeKb * 1024) {
            throw new SpecValidationException("Spec exceeds maximum allowed size of " + maxSizeKb + " KB");
        }

        Map<String, Object> spec = detectAndParse(input);
        validate(spec);
        return spec;
    }

    public String detectFormat(String input) {
        String trimmed = input.stripLeading();
        return (trimmed.startsWith("{") || trimmed.startsWith("[")) ? "json" : "yaml";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detectAndParse(String input) {
        try {
            if ("json".equals(detectFormat(input))) {
                return objectMapper.readValue(input, Map.class);
            } else {
                Yaml yaml = new Yaml();
                Object parsed = yaml.load(input);
                if (!(parsed instanceof Map)) {
                    throw new SpecValidationException("YAML spec must be a mapping object");
                }
                return (Map<String, Object>) parsed;
            }
        } catch (SpecValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SpecValidationException("Failed to parse spec: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void validate(Map<String, Object> spec) {
        if (!spec.containsKey("paths")) {
            throw new SpecValidationException("Spec must contain a 'paths' section");
        }

        Object pathsObj = spec.get("paths");
        if (!(pathsObj instanceof Map)) {
            throw new SpecValidationException("'paths' must be a mapping");
        }

        Map<String, Object> paths = (Map<String, Object>) pathsObj;
        int endpointCount = countEndpoints(paths);

        if (endpointCount == 0) {
            throw new SpecValidationException("Spec must define at least one endpoint");
        }
        if (endpointCount > MAX_ENDPOINTS) {
            throw new SpecValidationException(
                "Spec defines " + endpointCount + " endpoints; maximum supported is " + MAX_ENDPOINTS
            );
        }
    }

    @SuppressWarnings("unchecked")
    private int countEndpoints(Map<String, Object> paths) {
        int count = 0;
        for (Object pathItem : paths.values()) {
            if (pathItem instanceof Map<?, ?> methods) {
                for (Object key : methods.keySet()) {
                    String method = key.toString().toLowerCase();
                    if (method.equals("get") || method.equals("post") || method.equals("put")
                            || method.equals("patch") || method.equals("delete")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
