package com.example.apicodegen.web;

import com.example.apicodegen.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ResultController {

    private static final Logger log = LoggerFactory.getLogger(ResultController.class);

    private final SessionStore sessionStore;

    public ResultController(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping("/result/{id}")
    public String result(@PathVariable String id, Model model) {
        log.debug("GET /result/{} - fetching result", id);

        var resultOpt = sessionStore.getResult(id);
        if (resultOpt.isEmpty()) {
            log.warn("Result not found for session: {}", id);
            return "redirect:/";
        }

        var result = resultOpt.get();
        model.addAttribute("result", result);
        model.addAttribute("language", result.language().name().toLowerCase());
        model.addAttribute("files", result.files());
        model.addAttribute("review", result.reviewReport());

        log.info("Result page rendered: {} files, review score: {}",
                result.files().size(), result.reviewReport().overallScore());
        return "result";
    }
}
