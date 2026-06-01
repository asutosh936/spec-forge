package com.example.apicodegen.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record GenerationResult(
        String sessionId,
        List<GeneratedFile> files,
        Language language,
        Instant generatedAt
) {
    public Optional<GeneratedFile> findFile(String path) {
        return files.stream().filter(f -> f.filename().equals(path)).findFirst();
    }
}
