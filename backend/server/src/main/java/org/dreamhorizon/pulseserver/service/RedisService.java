package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.redis.RedisClient;

import java.util.ArrayList;
import java.util.List;
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

    // ==================== API KEY OPERATIONS ====================

    public Completable saveApiKeyMapping(String apiKey, String projectId) {
        log.debug("Saving API key mapping for project: {}", projectId);
        return redisClient.hset(API_KEY_MAP, apiKey, projectId)
            .ignoreElement()
            .doOnComplete(() -> log.debug("Saved API key mapping for project: {}", projectId))
            .doOnError(err -> log.error("Failed to save API key mapping for project: {}", projectId, err));
    }

    public Completable saveApiKeyMappings(Map<String, String> apiKeyToProjectId) {
        if (apiKeyToProjectId.isEmpty()) {
            log.debug("No API key mappings to save");
            return Completable.complete();
        }

        log.info("Saving {} API key mappings", apiKeyToProjectId.size());
        return redisClient.hset(API_KEY_MAP, apiKeyToProjectId)
            .ignoreElement()
            .doOnComplete(() -> log.info("Saved {} API key mappings", apiKeyToProjectId.size()))
            .doOnError(err -> log.error("Failed to save API key mappings", err));
    }

    public Completable replaceAllApiKeyMappings(Map<String, String> apiKeyToProjectId) {
        log.info("Replacing all API key mappings ({} keys) atomically", apiKeyToProjectId.size());

        return Completable.create(emitter -> {
            var redisAPI = redisClient.getRedisAPI();

            List<String> hsetArgs = new ArrayList<>();
            hsetArgs.add(API_KEY_MAP);
            apiKeyToProjectId.forEach((key, value) -> {
                hsetArgs.add(key);
                hsetArgs.add(value);
            });

            redisAPI.multi()
                .compose(v -> redisAPI.del(List.of(API_KEY_MAP)))
                .compose(v -> {
                    if (!apiKeyToProjectId.isEmpty()) {
                        return redisAPI.hset(hsetArgs);
                    }
                    return Future.succeededFuture();
                })
                .compose(v -> redisAPI.exec())
                .onSuccess(v -> {
                    log.info("Atomically replaced {} API key mappings", apiKeyToProjectId.size());
                    emitter.onComplete();
                })
                .onFailure(err -> {
                    log.error("Failed to replace API key mappings atomically", err);
                    emitter.onError(err);
                });
        });
    }

    public Maybe<String> getProjectIdByApiKey(String apiKey) {
        return redisClient.hget(API_KEY_MAP, apiKey)
            .doOnSuccess(projectId -> log.debug("Found project {} for API key", projectId))
            .doOnComplete(() -> log.debug("No project found for API key"));
    }

    public Completable removeApiKeyMapping(String apiKey) {
        log.debug("Removing API key mapping");
        return redisClient.hdel(API_KEY_MAP, apiKey)
            .ignoreElement()
            .doOnComplete(() -> log.debug("Removed API key mapping"))
            .doOnError(err -> log.error("Failed to remove API key mapping", err));
    }

    // ==================== PROJECT CREDIT OPERATIONS ====================

    public Completable saveProjectCredits(String projectId, long sessionCredits, long eventCredits) {
        String key = PROJECT_CREDIT_PREFIX + projectId + PROJECT_CREDIT_SUFFIX;
        log.debug("Saving credits for project: {} (session: {}, event: {})", projectId, sessionCredits, eventCredits);

        Map<String, String> credits = Map.of(
            "remaining_session_credit", String.valueOf(sessionCredits),
            "remaining_event_credit", String.valueOf(eventCredits)
        );

        return redisClient.hset(key, credits)
            .ignoreElement()
            .doOnComplete(() -> log.debug("Saved credits for project: {}", projectId))
            .doOnError(err -> log.error("Failed to save credits for project: {}", projectId, err));
    }

    public Single<Map<String, String>> getProjectCredits(String projectId) {
        String key = PROJECT_CREDIT_PREFIX + projectId + PROJECT_CREDIT_SUFFIX;
        return redisClient.hgetall(key)
            .doOnSuccess(credits -> log.debug("Retrieved credits for project: {}", projectId))
            .doOnError(err -> log.error("Failed to get credits for project: {}", projectId, err));
    }

    public Completable removeProjectCredits(String projectId) {
        String key = PROJECT_CREDIT_PREFIX + projectId + PROJECT_CREDIT_SUFFIX;
        return redisClient.del(key)
            .ignoreElement()
            .doOnComplete(() -> log.debug("Removed credits for project: {}", projectId))
            .doOnError(err -> log.error("Failed to remove credits for project: {}", projectId, err));
    }

    // ==================== LIFECYCLE ====================

    public void close() {
        redisClient.close();
    }
}

