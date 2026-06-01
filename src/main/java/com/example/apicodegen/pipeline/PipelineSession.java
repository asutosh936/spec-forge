package com.example.apicodegen.pipeline;

import com.example.apicodegen.model.Language;

public record PipelineSession(
        String sessionId,
        String spec,
        Language language,
        String additionalContext
) {}
