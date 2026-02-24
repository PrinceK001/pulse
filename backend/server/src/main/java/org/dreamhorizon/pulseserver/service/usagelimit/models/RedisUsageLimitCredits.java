package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents project usage credits for Redis storage.
 * Uses UsageLimitParameter as the single source of truth for credit keys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisUsageLimitCredits {
    private String projectId;
    
    /**
     * Map of UsageLimitParameter key to remaining credits.
     * Keys are from UsageLimitParameter.getKey() (e.g., "max_user_sessions_per_project")
     */
    @Builder.Default
    private Map<String, Long> remainingCredits = new HashMap<>();

    /**
     * Gets remaining credits for a specific parameter.
     */
    public Long getCredits(UsageLimitParameter param) {
        return remainingCredits.getOrDefault(param.getKey(), 0L);
    }

    /**
     * Sets remaining credits for a specific parameter.
     */
    public void setCredits(UsageLimitParameter param, Long value) {
        remainingCredits.put(param.getKey(), value);
    }

    /**
     * Creates RedisUsageLimitCredits from usage limits map (using finalThreshold values).
     * Used when initializing credits for a new project.
     */
    public static RedisUsageLimitCredits fromUsageLimits(String projectId, Map<String, UsageLimitValue> usageLimits) {
        Map<String, Long> credits = new HashMap<>();
        
        for (UsageLimitParameter param : UsageLimitParameter.values()) {
            UsageLimitValue limitValue = usageLimits.get(param.getKey());
            if (limitValue != null && limitValue.getFinalThreshold() != null) {
                credits.put(param.getKey(), limitValue.getFinalThreshold());
            } else if (limitValue != null && limitValue.getValue() != null) {
                // Fallback to value if finalThreshold not computed
                credits.put(param.getKey(), limitValue.getValue());
            } else {
                credits.put(param.getKey(), 0L);
            }
        }
        
        return RedisUsageLimitCredits.builder()
            .projectId(projectId)
            .remainingCredits(credits)
            .build();
    }

    /**
     * Converts to Redis hash map using redisCreditKey from UsageLimitParameter.
     */
    public Map<String, String> toRedisHash() {
        Map<String, String> redisHash = new HashMap<>();
        
        for (UsageLimitParameter param : UsageLimitParameter.values()) {
            Long credits = remainingCredits.getOrDefault(param.getKey(), 0L);
            redisHash.put(param.getRedisCreditKey(), String.valueOf(credits));
        }
        
        return redisHash;
    }

    /**
     * Creates RedisUsageLimitCredits from Redis hash map.
     */
    public static RedisUsageLimitCredits fromRedisHash(String projectId, Map<String, String> redisHash) {
        Map<String, Long> credits = new HashMap<>();
        
        for (UsageLimitParameter param : UsageLimitParameter.values()) {
            String value = redisHash.get(param.getRedisCreditKey());
            credits.put(param.getKey(), parseLong(value));
        }
        
        return RedisUsageLimitCredits.builder()
            .projectId(projectId)
            .remainingCredits(credits)
            .build();
    }

    private static Long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

