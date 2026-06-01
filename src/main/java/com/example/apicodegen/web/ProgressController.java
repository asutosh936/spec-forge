package com.example.apicodegen.web;

import com.example.apicodegen.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class ProgressController {

    private static final Logger log = LoggerFactory.getLogger(ProgressController.class);

    private final SessionStore sessionStore;

    public ProgressController(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping("/progress/{id}")
    public String progress(@PathVariable String id, Model model) {
        log.debug("GET /progress/{} - rendering progress page", id);
        model.addAttribute("sessionId", id);
        return "progress";
    }

    @GetMapping(value = "/progress/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter stream(@PathVariable String id) {
        log.debug("SSE stream connected for session: {}", id);
        return sessionStore.getEmitter(id).orElseThrow(() ->
            new IllegalArgumentException("Session not found: " + id)
        );
    }
}
