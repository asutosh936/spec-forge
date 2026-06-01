package com.example.apicodegen.store;

import com.example.apicodegen.model.GenerationResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private record SessionEntry(GenerationResult result, SseEmitter emitter, Instant createdAt, SessionStatus status) {}

    public enum SessionStatus { PENDING, RUNNING, DONE, FAILED }

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    // TODO: wire TTL from properties in Phase 2
    private static final long TTL_MINUTES = 30;

    public void registerEmitter(String sessionId, SseEmitter emitter) {
        sessions.put(sessionId, new SessionEntry(null, emitter, Instant.now(), SessionStatus.PENDING));
    }

    public Optional<SseEmitter> getEmitter(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(SessionEntry::emitter);
    }

    public void storeResult(String sessionId, GenerationResult result) {
        SessionEntry existing = sessions.get(sessionId);
        Instant createdAt = existing != null ? existing.createdAt() : Instant.now();
        sessions.put(sessionId, new SessionEntry(result, null, createdAt, SessionStatus.DONE));
    }

    public Optional<GenerationResult> getResult(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(SessionEntry::result);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(TTL_MINUTES * 60);
        sessions.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
