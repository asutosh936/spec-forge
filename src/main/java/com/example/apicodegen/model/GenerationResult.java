package com.example.apicodegen.model;

import java.util.List;

public record GenerationResult(
        String sessionId,
        Language language,
        ApiManifest manifest,
        List<GeneratedFile> files,
        ReviewReport reviewReport
) {}
