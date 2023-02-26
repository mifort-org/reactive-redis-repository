package org.mifort.example;

import org.mifort.repository.ReactiveRedisRepository;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class ExampleRepositoryImpl 
    extends ReactiveRedisRepository<ExampleEntity> implements ExampleRepository {

    public ExampleRepositoryImpl(ReactiveRedisTemplate<String, ExampleEntity> reactiveExampleEntityRedisTemplate,
            ObjectMapper objectMapper) {
        super(reactiveExampleEntityRedisTemplate, objectMapper, ExampleEntity.class);
    }

}
