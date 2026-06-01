package com.example.apicodegen.web;

import com.example.apicodegen.store.SessionStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ResultController {

    private final SessionStore sessionStore;

    public ResultController(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping("/result/{id}")
    public String result(@PathVariable String id, Model model) {
        var result = sessionStore.getResult(id);
        if (result.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("result", result.get());
        model.addAttribute("language", result.get().language().name().toLowerCase());
        return "result";
    }
}
