package org.mifort.example;

import reactor.core.publisher.Mono;

interface ExampleRepository {
    
    Mono<ExampleEntity> save(ExampleEntity session);
    
    Mono<Void> deleteById(String id);
    
    Mono<ExampleEntity> findById(String id);

}
