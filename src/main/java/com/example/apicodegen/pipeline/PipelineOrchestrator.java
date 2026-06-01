package com.example.apicodegen.pipeline;

import com.example.apicodegen.agent.ArchitectAgent;
import com.example.apicodegen.agent.CodeGeneratorAgent;
import com.example.apicodegen.agent.ReviewerAgent;
import com.example.apicodegen.agent.SpecAnalystAgent;
import com.example.apicodegen.agent.TestWriterAgent;
import com.example.apicodegen.exception.AgentException;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GeneratedFile;
import com.example.apicodegen.model.GenerationResult;
import com.example.apicodegen.model.ProjectBlueprint;
import com.example.apicodegen.model.ReviewReport;
import com.example.apicodegen.parser.SpecParser;
import com.example.apicodegen.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final SpecParser specParser;
    private final SpecAnalystAgent specAnalystAgent;
    private final ArchitectAgent architectAgent;
    private final CodeGeneratorAgent codeGeneratorAgent;
    private final TestWriterAgent testWriterAgent;
    private final ReviewerAgent reviewerAgent;
    private final SessionStore sessionStore;

    public PipelineOrchestrator(
            SpecParser specParser,
            SpecAnalystAgent specAnalystAgent,
            ArchitectAgent architectAgent,
            CodeGeneratorAgent codeGeneratorAgent,
            TestWriterAgent testWriterAgent,
            ReviewerAgent reviewerAgent,
            SessionStore sessionStore
    ) {
        this.specParser = specParser;
        this.specAnalystAgent = specAnalystAgent;
        this.architectAgent = architectAgent;
        this.codeGeneratorAgent = codeGeneratorAgent;
        this.testWriterAgent = testWriterAgent;
        this.reviewerAgent = reviewerAgent;
        this.sessionStore = sessionStore;
    }

    public String startAsync(PipelineSession session) {
        String sessionId = UUID.randomUUID().toString();
        log.info("Pipeline started: {} (language: {})", sessionId, session.language());

        sessionStore.registerEmitter(sessionId, new SseEmitter(180_000L));
        runPipeline(sessionId, session);

        return sessionId;
    }

    @Async
    protected void runPipeline(String sessionId, PipelineSession session) {
        try {
            // Step 1: Parse spec
            log.info("Pipeline {}: Step 1 - Parsing spec", sessionId);
            pushProgress(sessionId, "Spec Analyst", "running", "Parsing and analyzing spec...");
            specParser.parse(session.spec());

            ApiManifest manifest = specAnalystAgent.analyze(session.spec());
            pushProgress(sessionId, "Spec Analyst", "done", manifest.endpointCount() + " endpoints extracted");

            // Step 2: Design architecture
            log.info("Pipeline {}: Step 2 - Designing architecture", sessionId);
            pushProgress(sessionId, "Architect", "running", "Designing project structure...");
            ProjectBlueprint blueprint = architectAgent.design(manifest, session.language());
            pushProgress(sessionId, "Architect", "done", blueprint.filePlan().size() + " files planned");

            // Step 3: Generate code
            log.info("Pipeline {}: Step 3 - Generating code", sessionId);
            pushProgress(sessionId, "Code Generator", "running", "Generating source files in parallel...");
            List<GeneratedFile> sourceFiles = codeGeneratorAgent.generate(blueprint, manifest);
            pushProgress(sessionId, "Code Generator", "done", sourceFiles.size() + " files generated");

            // Step 4: Write tests
            log.info("Pipeline {}: Step 4 - Writing tests", sessionId);
            pushProgress(sessionId, "Test Writer", "running", "Generating test files...");
            List<GeneratedFile> testFiles = testWriterAgent.writeTests(sourceFiles, manifest);
            pushProgress(sessionId, "Test Writer", "done", testFiles.size() + " test files generated");

            // Combine all files
            List<GeneratedFile> allFiles = new ArrayList<>(sourceFiles);
            allFiles.addAll(testFiles);

            // Step 5: Review
            log.info("Pipeline {}: Step 5 - Reviewing code", sessionId);
            pushProgress(sessionId, "Reviewer", "running", "Reviewing generated code...");
            ReviewReport reviewReport = reviewerAgent.review(allFiles);
            pushProgress(sessionId, "Reviewer", "done", "Review complete: " + reviewReport.overallScore());

            // Store result
            GenerationResult result = new GenerationResult(sessionId, session.language(), manifest, allFiles, reviewReport);
            sessionStore.storeResult(sessionId, result);

            log.info("Pipeline {}: COMPLETED successfully", sessionId);
            pushProgress(sessionId, "Pipeline", "done", "Generation complete! Result available for download.");

        } catch (Exception e) {
            log.error("Pipeline {}: FAILED with error: {}", sessionId, e.getMessage(), e);
            pushProgress(sessionId, "Pipeline", "error", "Error: " + e.getMessage());
        }
    }

    private void pushProgress(String sessionId, String agentName, String status, String detail) {
        Optional<SseEmitter> emitterOpt = sessionStore.getEmitter(sessionId);
        if (emitterOpt.isEmpty()) {
            log.warn("No emitter found for session: {}", sessionId);
            return;
        }

        try {
            SseEmitter emitter = emitterOpt.get();
            String event = String.format(
                """
                    {"agent": "%s", "status": "%s", "detail": "%s"}
                    """,
                agentName, status, detail.replace("\"", "\\\"")
            );
            emitter.send(SseEmitter.event().name("progress").data(event));
            log.debug("Progress event sent: {} - {} ({})", agentName, status, detail);
        } catch (IOException e) {
            log.warn("Failed to send SSE event for session {}: {}", sessionId, e.getMessage());
        }
    }
}
