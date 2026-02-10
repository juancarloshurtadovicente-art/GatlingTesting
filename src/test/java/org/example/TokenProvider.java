package org.example;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class TokenProvider {
    private static final Object LOCK = new Object();

    private static final AtomicReference<String> TOKEN = new AtomicReference<>(null);
    private static final AtomicReference<Instant> EXPIRES_AT = new AtomicReference<>(Instant.EPOCH);

    private static final Duration SKEW = Duration.ofMinutes(5);
    private static final Duration TTL  = Duration.ofHours(8);

    public static String getValidToken() {
        Instant now = Instant.now();
        String token = TOKEN.get();
        Instant exp = EXPIRES_AT.get();

        if (token != null && !token.isBlank() && now.isBefore(exp.minus(SKEW))) {
            return token;
        }

        synchronized (LOCK) {
            now = Instant.now();
            token = TOKEN.get();
            exp = EXPIRES_AT.get();
            if (token != null && !token.isBlank() && now.isBefore(exp.minus(SKEW))) {
                return token;
            }

            String newToken = fetchTokenWithRetry(3, Duration.ofMillis(200));
            TOKEN.set(newToken);
            EXPIRES_AT.set(now.plus(TTL));
            return newToken;
        }
    }

    private static String fetchTokenWithRetry(int attempts, Duration baseSleep) {
        RuntimeException last = null;

        for (int i = 1; i <= attempts; i++) {
            try {
                String t = new ToolsController().getAPISEGToken();
                if (t == null || t.isBlank()) {
                    throw new RuntimeException("Token vacío/null devuelto por getAPISEGToken()");
                }
                return t;
            } catch (Exception e) {
                last = (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
                try {
                    Thread.sleep(baseSleep.toMillis() * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrumpido renovando token", ie);
                }
            }
        }
        throw last != null ? last : new RuntimeException("No se pudo obtener token");
    }
}
