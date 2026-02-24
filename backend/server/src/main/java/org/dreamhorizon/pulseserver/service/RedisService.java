package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.redis.RedisClient;
import org.dreamhorizon.pulseserver.service.usagelimit.models.RedisUsageLimitCredits;

import java.util.Map;

@Slf4j
@Singleton
public class RedisService {

    private static final String API_KEY_MAP = "{pulse}:apikey_map";
    private static final String PROJECT_CREDIT_PREFIX = "project:";
    private static final String PROJECT_CREDIT_SUFFIX = ":credit";

    private final RedisClient redisClient;

    @Inject
    public RedisService(RedisClient redisClient) {
        this.redisClient = redisClient;
        log.info("RedisService initialized");
    }

    /**
     * Saves a single API key to project ID mapping.
     * Key: {pulse}:apikey_map (Redis Hash)
     * Field: api_key
     * Value: project_id
     */
    public Completable saveApiKeyMapping(String apiKey, String projectId) {
        log.debug("Saving API key mapping for project: {}", projectId);
        return redisClient.hset(API_KEY_MAP, apiKey, projectId)
            .ignoreElement()
            .doOnComplete(() -> log.debug("Saved API key mapping for project: {}", projectId))
            .doOnError(err -> log.error("Failed to save API key mapping for project: {}", projectId, err));
    }

    /**
     * Saves usage limit credits for a single project.
     * Key pattern: project:{projectId}:credit (Redis Hash)
     * Uses UsageLimitParameter.redisCreditKey for hash field names.
     */
    public Completable saveUsageLimitCredits(RedisUsageLimitCredits credits) {
        String key = PROJECT_CREDIT_PREFIX + credits.getProjectId() + PROJECT_CREDIT_SUFFIX;
        Map<String, String> redisHash = credits.toRedisHash();
        
        log.debug("Saving credits for project: {} - {}", credits.getProjectId(), redisHash);

        return redisClient.hset(key, redisHash)
            .ignoreElement()
            .doOnComplete(() -> log.debug("Saved credits for project: {}", credits.getProjectId()))
            .doOnError(err -> log.error("Failed to save credits for project: {}", credits.getProjectId(), err));
    }

    public void close() {
        redisClient.close();
    }
}
