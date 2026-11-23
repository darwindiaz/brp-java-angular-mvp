package co.brp.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {

    private final Clock clock;
    private final int perSourceLimit;
    private final int globalLimit;
    private final Duration window;
    private final Map<String, Integer> attemptsBySource = new HashMap<>();
    private Instant windowEnd = Instant.MIN;
    private int totalAttempts;

    public LoginAttemptLimiter(Clock clock,
                               @Value("${brp.identity.login.max-attempts:10}") int perSourceLimit,
                               @Value("${brp.identity.login.max-total-attempts:120}") int globalLimit,
                               @Value("${brp.identity.login.window:PT1M}") Duration window) {
        if (perSourceLimit < 1 || globalLimit < perSourceLimit || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Los límites de login deben ser positivos y el global debe cubrir el individual.");
        }
        this.clock = clock;
        this.perSourceLimit = perSourceLimit;
        this.globalLimit = globalLimit;
        this.window = window;
    }

    public synchronized boolean tryAcquire(String source) {
        var now = clock.instant();
        if (!now.isBefore(windowEnd)) {
            attemptsBySource.clear();
            totalAttempts = 0;
            windowEnd = now.plus(window);
        }
        var sourceAttempts = attemptsBySource.getOrDefault(source, 0);
        if (sourceAttempts >= perSourceLimit || totalAttempts >= globalLimit) {
            return false;
        }
        attemptsBySource.put(source, sourceAttempts + 1);
        totalAttempts++;
        return true;
    }
}
