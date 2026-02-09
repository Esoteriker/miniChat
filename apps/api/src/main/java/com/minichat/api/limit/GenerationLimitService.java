package com.minichat.api.limit;

import com.minichat.api.common.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class GenerationLimitService {

    private final StringRedisTemplate redisTemplate;
    private final int qpsLimit;
    private final int inflightTtlSeconds;

    public GenerationLimitService(StringRedisTemplate redisTemplate,
                                  @Value("${app.limits.qps}") int qpsLimit,
                                  @Value("${app.limits.inflight-ttl-seconds}") int inflightTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.qpsLimit = qpsLimit;
        this.inflightTtlSeconds = inflightTtlSeconds;
    }

    public void enforceQps(UUID userId) {
        long sec = Instant.now().getEpochSecond();
        String key = "rl:" + userId + ":" + sec;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(2));
        }
        if (count != null && count > qpsLimit) {
            throw new TooManyRequestsException("QPS limit exceeded");
        }
    }

    public boolean tryAcquireInflight(UUID userId, UUID generationId) {
        String key = inflightKey(userId);
        Boolean ok = redisTemplate.opsForValue()
            .setIfAbsent(key, generationId.toString(), Duration.ofSeconds(inflightTtlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    public void releaseInflight(UUID userId, UUID generationId) {
        String key = inflightKey(userId);
        String value = redisTemplate.opsForValue().get(key);
        if (generationId.toString().equals(value)) {
            redisTemplate.delete(key);
        }
    }

    private String inflightKey(UUID userId) {
        return "user:" + userId + ":inflight_generation";
    }
}
