package com.demo.solana.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 限流服务
 * 支持基于Redis和内存的限流策略
 * 
 * @author Demo Development Team
 * @version 1.0.0
 */
@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    @Value("${app.security.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${app.security.rate-limit.requests-per-hour:1000}")
    private int requestsPerHour;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.security.rate-limit.use-redis:false}")
    private boolean useRedis;

    private final RedisTemplate<String, String> redisTemplate;

    // 内存限流存储 - 带过期清理
    private final ConcurrentHashMap<String, RateLimitInfo> memoryStore = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public RateLimitingService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 初始化定期清理任务
     */
    @PostConstruct
    public void init() {
        // 每30分钟清理一次过期数据
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 30, 30, TimeUnit.MINUTES);
        logger.info("限流服务初始化完成，已启动定期清理任务");
    }

    /**
     * 销毁时清理资源
     */
    @PreDestroy
    public void destroy() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("限流服务已销毁");
    }

    /**
     * 检查每分钟限流
     * 
     * @param clientId 客户端ID
     * @return 是否允许请求
     */
    public boolean isAllowedPerMinute(String clientId) {
        if (!rateLimitEnabled) {
            return true;
        }

        return checkRateLimit(clientId, "minute", requestsPerMinute, Duration.ofMinutes(1));
    }

    /**
     * 检查每小时限流
     * 
     * @param clientId 客户端ID
     * @return 是否允许请求
     */
    public boolean isAllowedPerHour(String clientId) {
        if (!rateLimitEnabled) {
            return true;
        }

        return checkRateLimit(clientId, "hour", requestsPerHour, Duration.ofHours(1));
    }

    /**
     * 通用限流检查
     * 
     * @param clientId 客户端ID
     * @param timeWindow 时间窗口类型
     * @param maxRequests 最大请求数
     * @param duration 时间窗口持续时间
     * @return 是否允许请求
     */
    private boolean checkRateLimit(String clientId, String timeWindow, int maxRequests, Duration duration) {
        try {
            if (useRedis) {
                return checkRateLimitWithRedis(clientId, timeWindow, maxRequests, duration);
            } else {
                return checkRateLimitWithMemory(clientId, timeWindow, maxRequests, duration);
            }
        } catch (Exception e) {
            logger.error("限流检查失败 - clientId: {}, timeWindow: {}, error: {}", 
                        clientId, timeWindow, e.getMessage(), e);
            // 发生异常时允许请求通过，避免影响业务
            return true;
        }
    }

    /**
     * 基于Redis的限流检查
     */
    private boolean checkRateLimitWithRedis(String clientId, String timeWindow, int maxRequests, Duration duration) {
        String key = String.format("rate_limit:%s:%s:%d", clientId, timeWindow, 
                                 System.currentTimeMillis() / duration.toMillis());

        try {
            String currentCountStr = redisTemplate.opsForValue().get(key);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;

            if (currentCount >= maxRequests) {
                logger.warn("Redis限流触发 - clientId: {}, timeWindow: {}, currentCount: {}, maxRequests: {}", 
                           clientId, timeWindow, currentCount, maxRequests);
                return false;
            }

            // 增加计数并设置过期时间
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, duration);

            logger.debug("Redis限流检查通过 - clientId: {}, timeWindow: {}, currentCount: {}, maxRequests: {}", 
                        clientId, timeWindow, currentCount + 1, maxRequests);

            return true;

        } catch (Exception e) {
            logger.error("Redis限流操作失败 - clientId: {}, key: {}, error: {}", 
                        clientId, key, e.getMessage(), e);
            return true; // Redis失败时允许请求
        }
    }

    /**
     * 基于内存的限流检查
     */
    private boolean checkRateLimitWithMemory(String clientId, String timeWindow, int maxRequests, Duration duration) {
        String key = clientId + ":" + timeWindow;
        long currentWindow = System.currentTimeMillis() / duration.toMillis();

        RateLimitInfo rateLimitInfo = memoryStore.computeIfAbsent(key, k -> new RateLimitInfo());

        synchronized (rateLimitInfo) {
            // 检查是否是新的时间窗口
            if (rateLimitInfo.windowStart != currentWindow) {
                rateLimitInfo.windowStart = currentWindow;
                rateLimitInfo.requestCount.set(0);
            }

            int currentCount = rateLimitInfo.requestCount.get();
            if (currentCount >= maxRequests) {
                logger.warn("内存限流触发 - clientId: {}, timeWindow: {}, currentCount: {}, maxRequests: {}", 
                           clientId, timeWindow, currentCount, maxRequests);
                return false;
            }

            rateLimitInfo.requestCount.incrementAndGet();
            
            logger.debug("内存限流检查通过 - clientId: {}, timeWindow: {}, currentCount: {}, maxRequests: {}", 
                        clientId, timeWindow, currentCount + 1, maxRequests);

            return true;
        }
    }

    /**
     * 获取剩余请求数
     * 
     * @param clientId 客户端ID
     * @param timeWindow 时间窗口
     * @return 剩余请求数
     */
    public int getRemainingRequests(String clientId, String timeWindow) {
        if (!rateLimitEnabled) {
            return Integer.MAX_VALUE;
        }

        try {
            int maxRequests = "minute".equals(timeWindow) ? requestsPerMinute : requestsPerHour;
            Duration duration = "minute".equals(timeWindow) ? Duration.ofMinutes(1) : Duration.ofHours(1);

            if (useRedis) {
                String key = String.format("rate_limit:%s:%s:%d", clientId, timeWindow, 
                                         System.currentTimeMillis() / duration.toMillis());
                String currentCountStr = redisTemplate.opsForValue().get(key);
                int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
                return Math.max(0, maxRequests - currentCount);
            } else {
                String key = clientId + ":" + timeWindow;
                RateLimitInfo rateLimitInfo = memoryStore.get(key);
                if (rateLimitInfo != null) {
                    long currentWindow = System.currentTimeMillis() / duration.toMillis();
                    if (rateLimitInfo.windowStart == currentWindow) {
                        return Math.max(0, maxRequests - rateLimitInfo.requestCount.get());
                    }
                }
                return maxRequests;
            }
        } catch (Exception e) {
            logger.error("获取剩余请求数失败 - clientId: {}, timeWindow: {}, error: {}", 
                        clientId, timeWindow, e.getMessage(), e);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * 清理过期的内存限流数据
     */
    public void cleanupExpiredEntries() {
        if (useRedis) {
            return; // Redis会自动过期
        }

        long currentTime = System.currentTimeMillis();
        memoryStore.entrySet().removeIf(entry -> {
            RateLimitInfo info = entry.getValue();
            // 清理超过2小时的数据
            return (currentTime - info.windowStart * Duration.ofHours(1).toMillis()) > Duration.ofHours(2).toMillis();
        });

        logger.debug("内存限流数据清理完成，当前条目数: {}", memoryStore.size());
    }

    /**
     * 内存限流信息
     */
    private static class RateLimitInfo {
        private volatile long windowStart;
        private final AtomicInteger requestCount = new AtomicInteger(0);
    }
}