package com.example.apicodegen.web;

import com.example.apicodegen.agent.SpecAnalystAgent;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GenerationRequest;
import com.example.apicodegen.parser.SpecParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class GeneratorController {

    private static final Logger log = LoggerFactory.getLogger(GeneratorController.class);

    private final SpecParser specParser;
    private final SpecAnalystAgent specAnalystAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeneratorController(SpecParser specParser, SpecAnalystAgent specAnalystAgent) {
        this.specParser = specParser;
        this.specAnalystAgent = specAnalystAgent;
    }

    @GetMapping("/")
    public String index(Model model) {
        log.debug("GET / - rendering index form");
        model.addAttribute("request", new GenerationRequest());
        return "index";
    }

    @PostMapping("/generate")
    @ResponseBody
    public String generate(@Valid @ModelAttribute GenerationRequest request, BindingResult bindingResult)
            throws JsonProcessingException {
        log.info("POST /generate - received request for language: {}", request.getLanguage());

        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().toString();
            log.warn("Validation errors in generation request: {}", errors);
            return "Validation errors: " + errors;
        }

        log.debug("Validating spec (size: {} bytes)", request.getSpec().length());
        specParser.parse(request.getSpec());

        log.info("Calling SpecAnalystAgent for analysis");
        ApiManifest manifest = specAnalystAgent.analyze(request.getSpec());

        String response = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        log.info("Generation request completed: {} endpoints analyzed", manifest.endpointCount());
        return response;
    }
}
