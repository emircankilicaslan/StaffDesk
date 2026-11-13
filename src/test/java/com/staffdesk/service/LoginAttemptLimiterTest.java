package com.staffdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    /** Testte zamanı elle ilerletebilmek için değiştirilebilir saat. */
    static class TestClock extends Clock {
        private Instant now = Instant.parse("2035-01-08T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 3, Duration.ofMinutes(15));

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAllowed(key);
            limiter.recordFailure(key);
        }
    }

    @Test
    void belowLimitIsAllowed() {
        fail("a@x.com", 2);
        assertThatCode(() -> limiter.checkAllowed("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void locksAfterMaxFailuresWithRetryAfter() {
        fail("a@x.com", 3);
        assertThatThrownBy(() -> limiter.checkAllowed("a@x.com"))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(15 * 60));
    }

    @Test
    void otherKeysAreNotAffected() {
        fail("a@x.com", 3);
        assertThatCode(() -> limiter.checkAllowed("b@x.com")).doesNotThrowAnyException();
    }

    @Test
    void keyIsCaseAndWhitespaceInsensitive() {
        fail("A@X.com", 3);
        assertThatThrownBy(() -> limiter.checkAllowed("  a@x.COM ")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void unlocksAfterLockoutExpires() {
        fail("a@x.com", 3);
        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.checkAllowed("a@x.com")).doesNotThrowAnyException();
        // süre dolduktan sonra sayac sıfırdan başlar: tek hata hemen kilitlemez
        limiter.recordFailure("a@x.com");
        assertThatCode(() -> limiter.checkAllowed("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void successResetsCounter() {
        fail("a@x.com", 2);
        limiter.recordSuccess("a@x.com");
        fail("a@x.com", 2);
        assertThatCode(() -> limiter.checkAllowed("a@x.com")).doesNotThrowAnyException();
    }
}
