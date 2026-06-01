package com.example.apicodegen.prompt;

import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.ProjectBlueprint;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    @Value("classpath:prompts/codegen-system.txt")
    private Resource systemPromptResource;

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private String systemPrompt;

    @PostConstruct
    void init() throws IOException {
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        log.info("System prompt loaded: {} bytes", systemPrompt.length());
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String buildUserMessage(ApiManifest manifest, ProjectBlueprint blueprint, String context) {
        try {
            UserMessage msg = new UserMessage(
                    blueprint.language().name(),
                    context,
                    new ManifestView(manifest.title(), manifest.version(),
                            manifest.description(), manifest.endpoints(), manifest.schemas()),
                    blueprint.filePlan().stream()
                            .map(f -> new FileSpec(f.path(), f.fileType(), f.description()))
                            .toList()
            );
            String json = mapper.writeValueAsString(msg);
            log.debug("User message built: {} bytes, {} files", json.length(), blueprint.filePlan().size());
            return json;
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialise prompt user message", e);
        }
    }

    // ── inner DTOs (Jackson serialises these) ─────────────────────────────────

    record UserMessage(
            String language,
            String context,
            ManifestView manifest,
            List<FileSpec> filesToGenerate
    ) {}

    record ManifestView(
            String title,
            String version,
            String description,
            List<ApiManifest.EndpointInfo> endpoints,
            List<ApiManifest.SchemaInfo> schemas
    ) {}

    record FileSpec(
            String path,
            String type,
            String description
    ) {}
}
