package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WebSocketDrainService {

    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicReference<Instant> drainStartedAt = new AtomicReference<>(null);

    public boolean isDraining() {
        return draining.get();
    }

    public Optional<Instant> getDrainStartedAt() {
        return Optional.ofNullable(drainStartedAt.get());
    }

    public void setDraining(boolean enabled) {
        draining.set(enabled);
        if (enabled) {
            drainStartedAt.compareAndSet(null, Instant.now());
            return;
        }
        drainStartedAt.set(null);
    }
}
