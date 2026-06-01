package com.example.apicodegen.web;

import com.example.apicodegen.store.SessionStore;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class ProgressController {

    private final SessionStore sessionStore;

    public ProgressController(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping(value = "/progress/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter progress(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(180_000L);
        sessionStore.registerEmitter(id, emitter);
        return emitter;
    }
}
