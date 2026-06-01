package com.example.apicodegen.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

@Component
public class SpecParser {

    private static final Logger log = LoggerFactory.getLogger(SpecParser.class);
    private static final int MAX_ENDPOINTS = 10;

    @Value("${codegen.spec.max-size-kb:128}")
    private int maxSizeKb = 128;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> parse(String input) {
        if (input == null || input.isBlank()) {
            log.warn("Spec validation failed: input is empty or null");
            throw new SpecValidationException("Spec must not be empty");
        }

        int sizeKb = input.length() / 1024;
        if (input.length() > maxSizeKb * 1024) {
            log.warn("Spec validation failed: size {} KB exceeds max {} KB", sizeKb, maxSizeKb);
            throw new SpecValidationException("Spec exceeds maximum allowed size of " + maxSizeKb + " KB");
        }

        log.debug("Parsing spec of size {} KB", sizeKb);
        String format = detectFormat(input);
        log.debug("Detected format: {}", format);

        Map<String, Object> spec = detectAndParse(input);
        validate(spec);
        log.info("Spec parsed and validated successfully");
        return spec;
    }

    public String detectFormat(String input) {
        String trimmed = input.stripLeading();
        String format = (trimmed.startsWith("{") || trimmed.startsWith("[")) ? "json" : "yaml";
        log.debug("Format detection: {}", format);
        return format;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detectAndParse(String input) {
        try {
            if ("json".equals(detectFormat(input))) {
                log.debug("Parsing as JSON");
                return objectMapper.readValue(input, Map.class);
            } else {
                log.debug("Parsing as YAML");
                Yaml yaml = new Yaml();
                Object parsed = yaml.load(input);
                if (!(parsed instanceof Map)) {
                    log.warn("YAML parse result is not a Map: {}", parsed.getClass().getSimpleName());
                    throw new SpecValidationException("YAML spec must be a mapping object");
                }
                return (Map<String, Object>) parsed;
            }
        } catch (SpecValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse spec", e);
            throw new SpecValidationException("Failed to parse spec: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void validate(Map<String, Object> spec) {
        if (!spec.containsKey("paths")) {
            log.warn("Spec validation failed: no 'paths' section");
            throw new SpecValidationException("Spec must contain a 'paths' section");
        }

        Object pathsObj = spec.get("paths");
        if (!(pathsObj instanceof Map)) {
            log.warn("Spec validation failed: 'paths' is not a mapping");
            throw new SpecValidationException("'paths' must be a mapping");
        }

        Map<String, Object> paths = (Map<String, Object>) pathsObj;
        int endpointCount = countEndpoints(paths);

        if (endpointCount == 0) {
            log.warn("Spec validation failed: no endpoints found");
            throw new SpecValidationException("Spec must define at least one endpoint");
        }
        if (endpointCount > MAX_ENDPOINTS) {
            log.warn("Spec validation failed: {} endpoints exceeds max {}", endpointCount, MAX_ENDPOINTS);
            throw new SpecValidationException(
                "Spec defines " + endpointCount + " endpoints; maximum supported is " + MAX_ENDPOINTS
            );
        }

        log.info("Spec validated: {} endpoint(s) found", endpointCount);
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

