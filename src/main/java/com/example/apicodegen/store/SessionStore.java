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

    private final Map<String, GenerationResult> store = new ConcurrentHashMap<>();

    @Value("${codegen.session.ttl-minutes:30}")
    private int ttlMinutes;

    public void save(GenerationResult result) {
        store.put(result.sessionId(), result);
        log.info("Session saved: {} ({} files)", result.sessionId(), result.files().size());
    }

    public Optional<GenerationResult> find(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    @Scheduled(fixedDelay = 600_000)
    public void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds((long) ttlMinutes * 60);
        int before = store.size();
        store.values().removeIf(r -> r.generatedAt().isBefore(cutoff));
        int removed = before - store.size();
        if (removed > 0) {
            log.info("Session cleanup: removed {} expired sessions ({} remaining)", removed, store.size());
        }
    }
}
