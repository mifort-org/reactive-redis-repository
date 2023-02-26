package org.mifort.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@EnableRedisRepositories
public class ExampleRepositoryConfig {

    @Bean
    public ReactiveRedisTemplate<String, ExampleEntity> reactiveExampleEntityRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        RedisSerializationContext<String, ExampleEntity> serializationContext = RedisSerializationContext
                .<String, ExampleEntity>newSerializationContext(RedisSerializer.string())
                .key(RedisSerializer.string())
                .value(new GenericToStringSerializer<>(ExampleEntity.class))
                .hashKey(RedisSerializer.string())
                .hashValue(RedisSerializer.json())
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

}