package com.lapzone.lapzoneweb.model.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiChatRateLimiter {

    private static final long MINUTE_MILLIS = 60_000;
    private static final long DAY_MILLIS = 86_400_000;

    private final int maxPerIpPerMinute;
    private final int maxPerIpPerDay;
    private final int maxGlobalPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, IpWindows> ipRequests = new ConcurrentHashMap<>();
    private final Deque<Long> globalRequests = new ArrayDeque<>();

    @Autowired
    public AiChatRateLimiter(
        @Value("${app.ai.rate-limit.per-ip-per-minute:6}") int maxPerIpPerMinute,
        @Value("${app.ai.rate-limit.per-ip-per-day:60}") int maxPerIpPerDay,
        @Value("${app.ai.rate-limit.global-per-minute:30}") int maxGlobalPerMinute
    ) {
        this(maxPerIpPerMinute, maxPerIpPerDay, maxGlobalPerMinute, Clock.systemUTC());
    }

    public AiChatRateLimiter(int maxPerIpPerMinute, int maxPerIpPerDay,
                             int maxGlobalPerMinute, Clock clock) {
        if (maxPerIpPerMinute < 1 || maxPerIpPerDay < 1 || maxGlobalPerMinute < 1) {
            throw new IllegalArgumentException("AI chat rate limits must be positive.");
        }
        this.maxPerIpPerMinute = maxPerIpPerMinute;
        this.maxPerIpPerDay = maxPerIpPerDay;
        this.maxGlobalPerMinute = maxGlobalPerMinute;
        this.clock = clock;
    }

    public synchronized long retryAfterSeconds(String ipAddress) {
        long now = clock.millis();
        prune(globalRequests, now, MINUTE_MILLIS);

        String key = ipAddress == null ? "" : ipAddress;
        IpWindows windows = ipRequests.get(key);
        long retryAfterSeconds = globalRequests.size() >= maxGlobalPerMinute
            ? secondsUntilAvailable(globalRequests, now, MINUTE_MILLIS) : 0;
        if (windows != null) {
            prune(windows.perMinute, now, MINUTE_MILLIS);
            prune(windows.perDay, now, DAY_MILLIS);
            long minuteRetry = windows.perMinute.size() >= maxPerIpPerMinute
                ? secondsUntilAvailable(windows.perMinute, now, MINUTE_MILLIS) : 0;
            long dayRetry = windows.perDay.size() >= maxPerIpPerDay
                ? secondsUntilAvailable(windows.perDay, now, DAY_MILLIS) : 0;
            retryAfterSeconds = Math.max(retryAfterSeconds, Math.max(minuteRetry, dayRetry));
        }
        if (retryAfterSeconds > 0) {
            return retryAfterSeconds;
        }
        if (windows == null) {
            windows = new IpWindows();
            ipRequests.put(key, windows);
        }

        globalRequests.addLast(now);
        windows.perMinute.addLast(now);
        windows.perDay.addLast(now);
        return 0;
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void cleanupExpiredEntries() {
        long now = clock.millis();
        prune(globalRequests, now, MINUTE_MILLIS);
        ipRequests.forEach((ipAddress, windows) -> {
            prune(windows.perMinute, now, MINUTE_MILLIS);
            prune(windows.perDay, now, DAY_MILLIS);
            if (windows.perDay.isEmpty()) {
                ipRequests.remove(ipAddress, windows);
            }
        });
    }

    private void prune(Deque<Long> timestamps, long now, long windowMillis) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - windowMillis) {
            timestamps.removeFirst();
        }
    }

    private long secondsUntilAvailable(Deque<Long> timestamps, long now, long windowMillis) {
        long remainingMillis = timestamps.peekFirst() + windowMillis - now;
        return Math.max(1, (remainingMillis + 999) / 1_000);
    }

    private static final class IpWindows {
        private final Deque<Long> perMinute = new ArrayDeque<>();
        private final Deque<Long> perDay = new ArrayDeque<>();
    }
}
