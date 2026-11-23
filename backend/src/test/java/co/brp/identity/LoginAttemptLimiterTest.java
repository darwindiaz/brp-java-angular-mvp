package co.brp.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import co.brp.identity.application.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginAttemptLimiterTest {

    @Test
    void admitsOnlyTheConfiguredAttemptsAndRecoversAtTheWindowBoundary() {
        var clock = mock(Clock.class);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        var limiter = new LoginAttemptLimiter(clock, 2, 4, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("source-a")).isTrue();
        assertThat(limiter.tryAcquire("source-a")).isTrue();
        assertThat(limiter.tryAcquire("source-a")).isFalse();
        when(clock.instant()).thenReturn(now.plusSeconds(59));
        assertThat(limiter.tryAcquire("source-a")).isFalse();
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThat(limiter.tryAcquire("source-a")).isTrue();
    }

    @Test
    void boundsAttemptsAcrossUniqueSources() {
        var clock = Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC);
        var limiter = new LoginAttemptLimiter(clock, 2, 3, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("source-a")).isTrue();
        assertThat(limiter.tryAcquire("source-a")).isTrue();
        assertThat(limiter.tryAcquire("source-b")).isTrue();
        assertThat(limiter.tryAcquire("source-c")).isFalse();
        assertThat(limiter.tryAcquire("source-b")).isFalse();
    }
}
