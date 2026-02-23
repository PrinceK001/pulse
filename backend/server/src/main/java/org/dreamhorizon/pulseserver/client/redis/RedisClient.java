package org.dreamhorizon.pulseserver.client.redis;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.Response;
import io.vertx.rxjava3.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RedisClient {

    private static final int DEFAULT_MAX_POOL_SIZE = 32;
    private static final int DEFAULT_MAX_POOL_WAITING = 128;

    private final Redis redis;
    private final RedisAPI redisAPI;

    public RedisClient(Vertx vertx, ApplicationConfig config) {
        String host = config.getRedisHost();
        int port = config.getRedisPort() != null ? config.getRedisPort() : 6379;
        int maxPoolSize = config.getRedisMaxPoolSize() != null ? config.getRedisMaxPoolSize() : DEFAULT_MAX_POOL_SIZE;
        int maxPoolWaiting = config.getRedisMaxPoolWaiting() != null ? config.getRedisMaxPoolWaiting() : DEFAULT_MAX_POOL_WAITING;

        log.info("Initializing RedisClient - host: {}, port: {}, maxPoolSize: {}", host, port, maxPoolSize);

        RedisOptions options = new RedisOptions()
            .setConnectionString("redis://" + host + ":" + port)
            .setMaxPoolSize(maxPoolSize)
            .setMaxPoolWaiting(maxPoolWaiting);

        this.redis = Redis.createClient(vertx.getDelegate(), options);
        this.redisAPI = RedisAPI.api(redis);

        log.info("RedisClient initialized successfully");
    }

    public Single<Response> hset(String key, String field, String value) {
        return Single.create(emitter -> {
            redisAPI.hset(List.of(key, field, value))
                .onSuccess(emitter::onSuccess)
                .onFailure(emitter::onError);
        });
    }

    public Single<Response> hset(String key, Map<String, String> fieldValues) {
        List<String> args = new java.util.ArrayList<>();
        args.add(key);
        fieldValues.forEach((field, value) -> {
            args.add(field);
            args.add(value);
        });

        return Single.create(emitter -> {
            redisAPI.hset(args)
                .onSuccess(emitter::onSuccess)
                .onFailure(emitter::onError);
        });
    }

    public Maybe<String> hget(String key, String field) {
        return Maybe.create(emitter -> {
            redisAPI.hget(key, field)
                .onSuccess(response -> {
                    if (response == null) {
                        emitter.onComplete();
                    } else {
                        emitter.onSuccess(response.toString());
                    }
                })
                .onFailure(emitter::onError);
        });
    }

    public Single<Map<String, String>> hgetall(String key) {
        return Single.create(emitter -> {
            redisAPI.hgetall(key)
                .onSuccess(response -> {
                    if (response == null) {
                        emitter.onSuccess(Map.of());
                    } else {
                        Map<String, String> result = response.getKeys().stream()
                            .collect(Collectors.toMap(
                                k -> k,
                                k -> response.get(k).toString()
                            ));
                        emitter.onSuccess(result);
                    }
                })
                .onFailure(emitter::onError);
        });
    }

    public Single<Long> del(String key) {
        return Single.create(emitter -> {
            redisAPI.del(List.of(key))
                .onSuccess(response -> emitter.onSuccess(response.toLong()))
                .onFailure(emitter::onError);
        });
    }

    public Single<Long> hdel(String key, String... fields) {
        List<String> args = new java.util.ArrayList<>();
        args.add(key);
        args.addAll(List.of(fields));

        return Single.create(emitter -> {
            redisAPI.hdel(args)
                .onSuccess(response -> emitter.onSuccess(response.toLong()))
                .onFailure(emitter::onError);
        });
    }

    public Completable multi() {
        return Completable.create(emitter -> {
            redisAPI.multi()
                .onSuccess(v -> emitter.onComplete())
                .onFailure(emitter::onError);
        });
    }

    public Single<Response> exec() {
        return Single.create(emitter -> {
            redisAPI.exec()
                .onSuccess(emitter::onSuccess)
                .onFailure(emitter::onError);
        });
    }

    public Completable discard() {
        return Completable.create(emitter -> {
            redisAPI.discard()
                .onSuccess(v -> emitter.onComplete())
                .onFailure(emitter::onError);
        });
    }

    public RedisAPI getRedisAPI() {
        return redisAPI;
    }

    public void close() {
        log.info("Closing Redis connection");
        if (redis != null) {
            redis.close();
        }
    }
}

