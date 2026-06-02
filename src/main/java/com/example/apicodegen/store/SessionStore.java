package com.example.apicodegen.store;

import com.example.apicodegen.model.GenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    public enum Status { RUNNING, DONE, FAILED }

    private record Entry(GenerationResult result, Status status, String error, Instant createdAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Value("${codegen.session.ttl-minutes:30}")
    private int ttlMinutes;

    /** Called immediately after POST /generate — before the background thread starts. */
    public void markRunning(String sessionId) {
        store.put(sessionId, new Entry(null, Status.RUNNING, null, Instant.now()));
        log.info("Session started: {}", sessionId);
    }

    /** Called by the background thread when generation succeeds. */
    public void save(GenerationResult result) {
        Entry existing = store.get(result.sessionId());
        Instant createdAt = existing != null ? existing.createdAt() : Instant.now();
        store.put(result.sessionId(), new Entry(result, Status.DONE, null, createdAt));
        log.info("Session complete: {} ({} files)", result.sessionId(), result.files().size());
    }

    /** Called by the background thread when generation fails. */
    public void markFailed(String sessionId, String errorMessage) {
        Entry existing = store.get(sessionId);
        Instant createdAt = existing != null ? existing.createdAt() : Instant.now();
        store.put(sessionId, new Entry(null, Status.FAILED, errorMessage, createdAt));
        log.warn("Session failed: {} — {}", sessionId, errorMessage);
    }

    public Optional<GenerationResult> find(String sessionId) {
        return Optional.ofNullable(store.get(sessionId)).map(Entry::result);
    }

    public Optional<Status> getStatus(String sessionId) {
        return Optional.ofNullable(store.get(sessionId)).map(Entry::status);
    }

    /** Returns a JSON-friendly map polled by waiting.html every 3 seconds. */
    public Map<String, String> getStatusInfo(String sessionId) {
        Entry entry = store.get(sessionId);
        if (entry == null) return Map.of("status", "not_found");
        return switch (entry.status()) {
            case RUNNING -> Map.of("status", "running");
            case DONE    -> Map.of("status", "done", "sessionId", sessionId);
            case FAILED  -> Map.of("status", "failed",
                                   "error", entry.error() != null ? entry.error() : "Unknown error");
        };
    }

    @Scheduled(fixedDelay = 600_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds((long) ttlMinutes * 60);
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
        int removed = before - store.size();
        if (removed > 0) {
            log.info("Session cleanup: removed {} expired sessions ({} remaining)", removed, store.size());
        }
    }
}
