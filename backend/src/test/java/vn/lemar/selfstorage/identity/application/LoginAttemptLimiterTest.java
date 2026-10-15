package vn.lemar.selfstorage.identity.application;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.lemar.selfstorage.identity.application.exception.TooManyLoginAttemptsException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptLimiterTest {

    private static final String EMAIL = "customer@example.com";
    private static final String IP = "203.0.113.7";
    private static final String EMAIL_KEY = "auth:login-fail:email:" + EMAIL;
    private static final String IP_KEY = "auth:login-fail:ip:" + IP;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginAttemptLimiter(redis, 5, 20, WINDOW);
    }

    @Test
    void rejectsNonPositiveAttemptsAndWindow() {
        assertThatThrownBy(() -> new LoginAttemptLimiter(redis, 0, 20, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptLimiter(redis, 5, 0, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptLimiter(redis, 5, 20, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsWhenCountersAreBelowThresholds() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EMAIL_KEY)).thenReturn("4");
        when(valueOps.get(IP_KEY)).thenReturn("19");

        assertThatCode(() -> limiter.checkOrThrow(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void allowsWhenCountersAreMissing() {
        when(redis.opsForValue()).thenReturn(valueOps);

        assertThatCode(() -> limiter.checkOrThrow(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void allowsWhenStoredCounterIsCorrupt() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EMAIL_KEY)).thenReturn("not-a-number");

        assertThatCode(() -> limiter.checkOrThrow(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenEmailReachesThreshold() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EMAIL_KEY)).thenReturn("5");

        assertThatThrownBy(() -> limiter.checkOrThrow(EMAIL, IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void blocksWhenIpReachesThreshold() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EMAIL_KEY)).thenReturn("0");
        when(valueOps.get(IP_KEY)).thenReturn("20");

        assertThatThrownBy(() -> limiter.checkOrThrow(EMAIL, IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> limiter.checkOrThrow(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void recordFailureIncrementsBothCountersAndSetsWindowOnFirstHit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(EMAIL_KEY)).thenReturn(1L);
        when(valueOps.increment(IP_KEY)).thenReturn(1L);

        limiter.recordFailure(EMAIL, IP);

        verify(redis).expire(EMAIL_KEY, WINDOW);
        verify(redis).expire(IP_KEY, WINDOW);
    }

    @Test
    void recordFailureKeepsOriginalWindowOnLaterHits() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(EMAIL_KEY)).thenReturn(2L);
        when(valueOps.increment(IP_KEY)).thenReturn(3L);

        limiter.recordFailure(EMAIL, IP);

        verify(redis, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void recordFailureFailsOpenWhenRedisIsDown() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> limiter.recordFailure(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void resetDeletesEmailCounter() {
        limiter.reset(EMAIL);

        verify(redis).delete(EMAIL_KEY);
    }

    @Test
    void resetFailsOpenWhenRedisIsDown() {
        when(redis.delete(EMAIL_KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> limiter.reset(EMAIL)).doesNotThrowAnyException();
    }
}
