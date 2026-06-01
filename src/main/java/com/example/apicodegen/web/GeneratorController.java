package com.example.apicodegen.web;

import com.example.apicodegen.agent.SpecAnalystAgent;
import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.GenerationRequest;
import com.example.apicodegen.parser.SpecParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class GeneratorController {

    private final SpecParser specParser;
    private final SpecAnalystAgent specAnalystAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeneratorController(SpecParser specParser, SpecAnalystAgent specAnalystAgent) {
        this.specParser = specParser;
        this.specAnalystAgent = specAnalystAgent;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("request", new GenerationRequest());
        return "index";
    }

    @PostMapping("/generate")
    @ResponseBody
    public String generate(@Valid @ModelAttribute GenerationRequest request, BindingResult bindingResult)
            throws JsonProcessingException {
        if (bindingResult.hasErrors()) {
            return "Validation errors: " + bindingResult.getAllErrors();
        }
        specParser.parse(request.getSpec());
        ApiManifest manifest = specAnalystAgent.analyze(request.getSpec());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
    }
}
