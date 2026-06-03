package com.ewallet.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {

    private final int loginMaxRequests;
    private final int transferMaxRequests;
    private final Duration window;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.rate-limit.login.max-requests:10}") int loginMaxRequests,
            @Value("${app.rate-limit.transfer.max-requests:30}") int transferMaxRequests,
            @Value("${app.rate-limit.window-seconds:60}") int windowSeconds) {
        this.loginMaxRequests = loginMaxRequests;
        this.transferMaxRequests = transferMaxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public void checkLoginLimit(String clientKey) {
        checkLimit("login:" + clientKey, loginMaxRequests);
    }

    public void checkTransferLimit(String clientKey) {
        checkLimit("transfer:" + clientKey, transferMaxRequests);
    }

    private void checkLimit(String bucketKey, int maxRequests) {
        long windowStart = currentWindowStart();
        WindowCounter counter = counters.computeIfAbsent(bucketKey, k -> new WindowCounter());

        synchronized (counter) {
            if (counter.windowStart != windowStart) {
                counter.windowStart = windowStart;
                counter.count.set(0);
            }
            if (counter.count.incrementAndGet() > maxRequests) {
                log.warn("Rate limit exceeded for bucket={}", bucketKey);
                throw new com.ewallet.exception.RateLimitExceededException(
                        "Too many requests. Please try again later.");
            }
        }
    }

    private long currentWindowStart() {
        long windowMillis = window.toMillis();
        return System.currentTimeMillis() / windowMillis;
    }

    private static class WindowCounter {
        private volatile long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);
    }
}
