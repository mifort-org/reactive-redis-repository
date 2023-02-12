package org.mifort.repository;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Base reactive repository for Redis communication.
 * {@link @RedisHash} annotation is required for entity to store.<br>
 * 
 * Limitations in the current implementation:<br>
 * 1) {@link @TimeToLive} should be under a method. It's optional.<br>
 * 2) {@link @Id} should be used once. Only the first will be used<br>
 * 3) {@link @Id} and {@link @Indexed} annotations should be specified for fields (not methods)<br>
 * 4) {@link @Indexed} fields are supported via SET and there is no support for removing indexed sets after expiration event<br>
 * 5) Phantom entities are not created (It's opposite to default not reactive Spring Data Redis implementation)<br>
 * 
 * @author Andrew Voitov
 * 
 * @version 1.0
 *
 * @param <T> Entity type
 */
@Slf4j
public class ReactiveRedisRepository <T> {
    
    private static final String SPACE_SEPARATOR = ":";
    private final ReactiveRedisTemplate<String, T> reactiveRedisTemplate;
    private final Class<T> entityClass;
    private final ObjectMapper objectMapper;
    private final RedisHash hashConfig;
    
    public ReactiveRedisRepository (ReactiveRedisTemplate<String, T> reactiveRedisTemplate,
            ObjectMapper objectMapper,
            Class<T> entityClass) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.entityClass = entityClass;
        this.objectMapper = objectMapper;
        this.hashConfig = getRedisHashData();
    }
    
    public Mono<T> findById(String id) {
        if (hashConfig == null) {
            return Mono.empty();
        }
        
        return reactiveRedisTemplate.<String, T>opsForHash()
                .scan(hashConfig.value() + SPACE_SEPARATOR + id)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .filter(MapUtils::isNotEmpty)
                .map(map ->  objectMapper.convertValue(map, entityClass));
    
    }
    
    public Mono<T> save(T entity) {
        if (hashConfig == null) {
            return Mono.empty();
        }
        
        if (StringUtils.isEmpty(getId(entity))) {
            setId(entity);
        }
        
        String filledId = getId(entity);
        
        String key = hashConfig.value() + SPACE_SEPARATOR + filledId;
        
        Map<Object, Object> sessionToMap = new BeanMap(entity);
        
        return reactiveRedisTemplate.opsForHash()
            .putAll(key, sessionToMap)
            .flatMap(result -> {
                ImmutablePair<Long, TimeUnit> ttl = findTTL(entity);
                if (Boolean.TRUE.equals(result) && ttl != null) {
                    Duration duration = Duration.ZERO;
                    if (TimeUnit.MILLISECONDS.equals(ttl.getValue())) {
                        duration = Duration.ofMillis(ttl.getKey());
                    }
                    if (TimeUnit.SECONDS.equals(ttl.getValue())) {
                        duration = Duration.ofSeconds(ttl.getKey());
                    }
                    if (TimeUnit.HOURS.equals(ttl.getValue())) {
                        duration = Duration.ofHours(ttl.getKey());
                    }
                    if (TimeUnit.DAYS.equals(ttl.getValue())) {
                        duration = Duration.ofDays(ttl.getKey());
                    }
                    if (TimeUnit.NANOSECONDS.equals(ttl.getValue())) {
                        duration = Duration.ofNanos(ttl.getKey());
                    }
                    if (TimeUnit.MICROSECONDS.equals(ttl.getValue())) {
                        duration = Duration.ofNanos(ttl.getKey() * 1000);
                    }
                    return reactiveRedisTemplate.expire(key, duration)
                            .thenReturn(entity);
                }
                
                return Mono.just(entity);
            })
            .flatMap(savedEntity -> saveIndexedFields(savedEntity, hashConfig.value(), filledId));
    }
    
    public Mono<Void> deleteById(String id) {
        if (hashConfig == null) {
            return Mono.empty();
        }
        
        return removeIndexedFields(hashConfig.value(), id)
                .then(reactiveRedisTemplate.opsForHash()
                        .delete(hashConfig.value() + SPACE_SEPARATOR + id)
                        .then());
    }
    
    private Mono<T> saveIndexedFields(T entity, String space, String id) {
        List<ImmutablePair<String, String>> indexedFields = getIndexedFIelds(entity);
        if (CollectionUtils.isEmpty(indexedFields)) {
            return Mono.just(entity);
        }
        
        return Flux.fromIterable(indexedFields)
            .flatMap(fieldMeta -> 
                reactiveRedisTemplate.opsForSet(RedisSerializationContext.string())
                    .add(space + SPACE_SEPARATOR + fieldMeta.getKey() +  SPACE_SEPARATOR + fieldMeta.getValue(), id)
            )
            .then(Mono.just(entity));
    }
    
    private Mono<Void> removeIndexedFields(String space, String id) {
        return this.findById(id)
            .flatMap(entity -> {
                List<ImmutablePair<String, String>> indexedFields = getIndexedFIelds(entity);
                if (CollectionUtils.isEmpty(indexedFields)) {
                    return Mono.just(entity);
                }
                
                return Flux.fromIterable(indexedFields)
                    .flatMap(fieldMeta -> 
                        reactiveRedisTemplate.opsForSet()
                            .remove(space + SPACE_SEPARATOR + fieldMeta.getKey() +  SPACE_SEPARATOR + fieldMeta.getValue(), id)
                    )
                    .then();
            }).then();
    }
    
    private List<ImmutablePair<String, String>> getIndexedFIelds(T entity) {
        return Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Indexed.class))
                .map(field -> {
                    try {
                        Object property = PropertyUtils.getProperty(entity, field.getName());
                        if (property != null) {
                            return ImmutablePair.of(field.getName(), property.toString());
                        }
                    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        log.warn("It's not possible to read @Indexed field via getter", e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    private String getId(T entity) {
        return Arrays.stream(entity.getClass().getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(Id.class))
            .findFirst()
            .map(field -> {
                try {
                    Object property = PropertyUtils.getProperty(entity, field.getName());
                    if (property != null) {
                        return property.toString();
                    }
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    log.warn("It's not possible to read @Id field via getter", e);
                }
                return null;
            })
            .orElse(null);
    }
    
    private void setId(T entity) {
        Arrays.stream(entity.getClass().getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(Id.class))
        .findFirst()
        .ifPresent(field -> {
            try {
                PropertyUtils.setSimpleProperty(entity, field.getName(), UUID.randomUUID().toString());
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                log.warn("It's not possible to set @Id field via setter", e);
            }
        });
    }
    
    private ImmutablePair<Long, TimeUnit> findTTL(Object obj) {
        ImmutablePair<Long, TimeUnit> result = Arrays.stream(entityClass.getMethods())
            .filter(method -> AnnotationUtils.getAnnotation(method, TimeToLive.class) != null)
            .findFirst()
            .map(method -> {
                try {
                    Object ttl = method.invoke(obj, (Object[]) null);
                    TimeToLive ttlAnnotation = method.getAnnotation(TimeToLive.class);
                    
                    log.debug("TTL from method {} will be used", method.getName());
                    return ImmutablePair.of((Long) ttl, ttlAnnotation.unit());
                } catch (Exception e) {
                    log.debug("There is no @TimeToLive annotation. We will try to use TTL from @RedisHash", e);
                } 
                return null;
            })
            .orElse(null);
        
        if (result == null && hashConfig != null) {
            log.debug("TTL class level Annotation @RedisHash will be used. Class: {}", entityClass.getName());
            return ImmutablePair.of(hashConfig.timeToLive(), TimeUnit.SECONDS);
        }
        
        return result;
    }
    
    private RedisHash getRedisHashData() {
        RedisHash hashConfigData = AnnotationUtils.findAnnotation(entityClass, RedisHash.class);
        if (hashConfigData == null) {
            log.warn("No @RedisHash annotation for {}", entityClass);
        }
        return hashConfigData;
    }
}
