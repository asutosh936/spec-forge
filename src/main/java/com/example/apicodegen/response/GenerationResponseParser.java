package com.example.apicodegen.response;

import com.example.apicodegen.exception.AgentException;
import com.example.apicodegen.model.GeneratedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GenerationResponseParser {

    private static final Logger log = LoggerFactory.getLogger(GenerationResponseParser.class);
    private static final int MAX_DEBUG_LENGTH = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    public List<GeneratedFile> parse(String rawResponse) {
        String cleaned = stripMarkdownFences(rawResponse.trim());

        JsonNode root;
        try {
            root = mapper.readTree(cleaned);
        } catch (Exception e) {
            // Detect truncation: response starts like JSON but is missing its closing braces
            if (cleaned.startsWith("{") && !cleaned.trim().endsWith("}")) {
                throw new AgentException(
                    "Generation response was truncated — the spec produced too many files for a single call. " +
                    "Try a simpler spec (fewer endpoints or schemas).");
            }
            String preview = cleaned.length() > MAX_DEBUG_LENGTH
                    ? cleaned.substring(0, MAX_DEBUG_LENGTH) + "..." : cleaned;
            throw new AgentException("LLM response is not valid JSON: " + preview);
        }

        JsonNode filesNode = root.path("files");
        if (filesNode.isMissingNode() || !filesNode.isArray()) {
            throw new AgentException("Claude response missing 'files' array");
        }
        if (filesNode.isEmpty()) {
            throw new AgentException("Claude returned an empty 'files' array");
        }

        List<GeneratedFile> files = new ArrayList<>();
        for (JsonNode fileNode : filesNode) {
            String path    = fileNode.path("path").asText(null);
            String content = fileNode.path("content").asText(null);
            String type    = fileNode.path("type").asText("OTHER");

            if (path == null || path.isBlank()) {
                log.warn("Skipping file entry with missing path");
                continue;
            }
            if (content == null) {
                log.warn("Skipping file {} — missing content", path);
                continue;
            }

            files.add(new GeneratedFile(path, stripMarkdownFences(content), type));
        }

        log.info("Parsed {} generated files from Claude response", files.size());
        return files;
    }

    private String stripMarkdownFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("```[a-zA-Z]*\\n?", "");
            int closing = t.lastIndexOf("```");
            if (closing >= 0) t = t.substring(0, closing);
            t = t.trim();
        }
        return t;
    }
}
