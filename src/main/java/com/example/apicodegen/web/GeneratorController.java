package com.example.apicodegen.web;

import com.example.apicodegen.agent.CodeGenerationAgent;
import com.example.apicodegen.blueprint.ProjectBlueprintBuilder;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GenerationRequest;
import com.example.apicodegen.model.GenerationResult;
import com.example.apicodegen.model.ProjectBlueprint;
import com.example.apicodegen.parser.SpecAnalyzer;
import com.example.apicodegen.parser.SpecParser;
import com.example.apicodegen.parser.SpecValidationException;
import com.example.apicodegen.store.SessionStore;
import com.example.apicodegen.zip.ZipBuilder;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
public class GeneratorController {

    private static final Logger log = LoggerFactory.getLogger(GeneratorController.class);

    private final SpecParser specParser;
    private final SpecAnalyzer specAnalyzer;
    private final ProjectBlueprintBuilder blueprintBuilder;
    private final CodeGenerationAgent codeGenerationAgent;
    private final ZipBuilder zipBuilder;
    private final SessionStore sessionStore;

    public GeneratorController(SpecParser specParser, SpecAnalyzer specAnalyzer,
                                ProjectBlueprintBuilder blueprintBuilder,
                                CodeGenerationAgent codeGenerationAgent,
                                ZipBuilder zipBuilder, SessionStore sessionStore) {
        this.specParser = specParser;
        this.specAnalyzer = specAnalyzer;
        this.blueprintBuilder = blueprintBuilder;
        this.codeGenerationAgent = codeGenerationAgent;
        this.zipBuilder = zipBuilder;
        this.sessionStore = sessionStore;
    }

    // ── Input form ────────────────────────────────────────────────────────────

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("request", new GenerationRequest());
        return "index";
    }

    // ── Submit: validate spec synchronously, then kick off background thread ──

    @PostMapping("/generate")
    public String generate(@Valid @ModelAttribute GenerationRequest request,
                           BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("error", "Please fix the errors below.");
            return "index";
        }

        // Spec validation is fast (no API call) — do it on the request thread
        // so the user sees an immediate error if the spec is invalid.
        try {
            specParser.parse(request.getSpec());
        } catch (SpecValidationException e) {
            log.warn("Spec validation failed: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("request", request);
            return "index";
        }

        String sessionId = UUID.randomUUID().toString();
        sessionStore.markRunning(sessionId);

        // Run the Claude call in a background thread so the HTTP response
        // returns immediately — avoids Render's (and any proxy's) request timeout.
        Thread thread = new Thread(() -> runGeneration(sessionId, request));
        thread.setName("gen-" + sessionId.substring(0, 8));
        thread.setDaemon(true);
        thread.start();

        log.info("Generation started — session: {}, language: {}", sessionId, request.getLanguage());
        return "redirect:/waiting/" + sessionId;
    }

    private void runGeneration(String sessionId, GenerationRequest request) {
        try {
            Map<String, Object> parsedSpec  = specParser.parse(request.getSpec());
            ApiManifest manifest            = specAnalyzer.analyze(parsedSpec);
            ProjectBlueprint blueprint      = blueprintBuilder.build(manifest, request.getLanguage());
            var files = codeGenerationAgent.generate(manifest, blueprint, request.getAdditionalContext());
            sessionStore.save(new GenerationResult(sessionId, files, request.getLanguage(), Instant.now()));
            log.info("Generation complete — session: {}, files: {}", sessionId, files.size());
        } catch (Exception e) {
            log.error("Generation failed for session {}: {}", sessionId, e.getMessage(), e);
            sessionStore.markFailed(sessionId, e.getMessage());
        }
    }

    // ── Waiting page (shown while background thread is running) ───────────────

    @GetMapping("/waiting/{sessionId}")
    public String waiting(@PathVariable String sessionId, Model model) {
        // Already done? Skip the waiting page entirely.
        var status = sessionStore.getStatus(sessionId);
        if (status.isPresent() && status.get() == SessionStore.Status.DONE) {
            return "redirect:/result/" + sessionId;
        }
        model.addAttribute("sessionId", sessionId);
        return "waiting";
    }

    // ── Status endpoint polled by waiting.html every 3 seconds ───────────────

    @GetMapping("/api/status/{sessionId}")
    @ResponseBody
    public Map<String, String> status(@PathVariable String sessionId) {
        return sessionStore.getStatusInfo(sessionId);
    }

    // ── Result page ───────────────────────────────────────────────────────────

    @GetMapping("/result/{sessionId}")
    public String result(@PathVariable String sessionId, Model model) {
        GenerationResult result = sessionStore.find(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session expired or not found"));
        model.addAttribute("result", result);
        model.addAttribute("files", result.files());
        model.addAttribute("firstFile", result.files().isEmpty() ? null : result.files().get(0));
        model.addAttribute("language", result.language().name().toLowerCase());
        return "result";
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @GetMapping("/download/{sessionId}")
    public ResponseEntity<byte[]> download(@PathVariable String sessionId) {
        GenerationResult result = sessionStore.find(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session expired or not found"));
        byte[] zip = zipBuilder.build(result);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"generated-project.zip\"")
                .body(zip);
    }
}
