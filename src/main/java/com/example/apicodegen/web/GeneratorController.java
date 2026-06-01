package com.example.apicodegen.web;

import com.example.apicodegen.pipeline.PipelineOrchestrator;
import com.example.apicodegen.pipeline.PipelineSession;
import com.example.apicodegen.model.GenerationRequest;
import com.example.apicodegen.parser.SpecParser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GeneratorController {

    private static final Logger log = LoggerFactory.getLogger(GeneratorController.class);

    private final SpecParser specParser;
    private final PipelineOrchestrator pipelineOrchestrator;

    public GeneratorController(SpecParser specParser, PipelineOrchestrator pipelineOrchestrator) {
        this.specParser = specParser;
        this.pipelineOrchestrator = pipelineOrchestrator;
    }

    @GetMapping("/")
    public String index(Model model) {
        log.debug("GET / - rendering index form");
        model.addAttribute("request", new GenerationRequest());
        return "index";
    }

    @PostMapping("/generate")
    public String generate(@Valid @ModelAttribute GenerationRequest request, BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        log.info("POST /generate - received request for language: {}", request.getLanguage());

        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().toString();
            log.warn("Validation errors in generation request: {}", errors);
            redirectAttributes.addFlashAttribute("error", "Validation errors: " + errors);
            return "redirect:/";
        }

        try {
            log.debug("Validating spec (size: {} bytes)", request.getSpec().length());
            specParser.parse(request.getSpec());

            log.info("Spec validated. Starting pipeline for language: {}", request.getLanguage());
            PipelineSession session = new PipelineSession(null, request.getSpec(), request.getLanguage(), request.getAdditionalContext());
            String sessionId = pipelineOrchestrator.startAsync(session);

            log.info("Pipeline started with session ID: {}", sessionId);
            return "redirect:/progress/" + sessionId;
        } catch (Exception e) {
            log.error("Error during generation request", e);
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/";
        }
    }
}
