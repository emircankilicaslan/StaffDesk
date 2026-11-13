package com.staffdesk.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * E-posta bazlı başarısız giriş sayacı: art arda {@code maxAttempts} hatalı denemeden sonra hesap
 * {@code lockout} süresince geçici olarak kilitlenir. Kayıtlı olmayan e-postalar da aynı şekilde sayılır;
 * böylece yanıttan hesabın var olup olmadığı anlaşılmaz. Sayaç bellekte tutulur (tek örnek için yeterli).
 */
@Component
public class LoginAttemptLimiter {

    private static final int PURGE_THRESHOLD = 10_000;

    private record State(int failures, Instant lockedUntil, Instant lastFailure) {}

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockout;

    @Autowired
    public LoginAttemptLimiter(Clock clock, @Value("${staffdesk.login.max-attempts:5}") int maxAttempts,
            @Value("${staffdesk.login.lockout-minutes:15}") long lockoutMinutes) {
        this(clock, maxAttempts, Duration.ofMinutes(lockoutMinutes));
    }

    LoginAttemptLimiter(Clock clock, int maxAttempts, Duration lockout) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("staffdesk.login.max-attempts en az 1 olmalı");
        }
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockout = lockout;
    }

    /** Hesap kilitliyse {@link TooManyAttemptsException} fırlatır. */
    public void checkAllowed(String key) {
        State s = states.get(normalize(key));
        if (s == null || s.lockedUntil() == null) {
            return;
        }
        Instant now = Instant.now(clock);
        if (now.isBefore(s.lockedUntil())) {
            throw new TooManyAttemptsException(Math.max(1, Duration.between(now, s.lockedUntil()).toSeconds()));
        }
    }

    public void recordFailure(String key) {
        Instant now = Instant.now(clock);
        if (states.size() > PURGE_THRESHOLD) {
            states.entrySet().removeIf(e -> !now.isBefore(e.getValue().lastFailure().plus(lockout)));
        }
        states.compute(normalize(key), (k, s) -> {
            int failures = 1;
            if (s != null && now.isBefore(s.lastFailure().plus(lockout))) {
                failures = s.failures() + 1;
            }
            Instant lockedUntil = failures >= maxAttempts ? now.plus(lockout) : null;
            return new State(failures, lockedUntil, now);
        });
    }

    public void recordSuccess(String key) {
        states.remove(normalize(key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
