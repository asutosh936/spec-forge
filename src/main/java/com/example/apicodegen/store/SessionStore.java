package com.example.apicodegen.store;

import com.example.apicodegen.model.GenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private record SessionEntry(GenerationResult result, SseEmitter emitter, Instant createdAt, SessionStatus status) {}

    public enum SessionStatus { PENDING, RUNNING, DONE, FAILED }

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    // TODO: wire TTL from properties in Phase 2
    private static final long TTL_MINUTES = 30;

    public void registerEmitter(String sessionId, SseEmitter emitter) {
        log.debug("Registering SSE emitter for session: {}", sessionId);
        sessions.put(sessionId, new SessionEntry(null, emitter, Instant.now(), SessionStatus.PENDING));
        log.info("Session registered: {}", sessionId);
    }

    public Optional<SseEmitter> getEmitter(String sessionId) {
        log.debug("Retrieving SSE emitter for session: {}", sessionId);
        return Optional.ofNullable(sessions.get(sessionId)).map(SessionEntry::emitter);
    }

    public void storeResult(String sessionId, GenerationResult result) {
        SessionEntry existing = sessions.get(sessionId);
        Instant createdAt = existing != null ? existing.createdAt() : Instant.now();
        sessions.put(sessionId, new SessionEntry(result, null, createdAt, SessionStatus.DONE));
        log.info("Session result stored: {} with {} files", sessionId, result.files().size());
    }

    public Optional<GenerationResult> getResult(String sessionId) {
        log.debug("Retrieving result for session: {}", sessionId);
        return Optional.ofNullable(sessions.get(sessionId)).map(SessionEntry::result);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(TTL_MINUTES * 60);
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
        int after = sessions.size();
        if (before != after) {
            log.info("Session store cleanup: removed {} expired sessions (total now: {})", before - after, after);
        }
    }
}
