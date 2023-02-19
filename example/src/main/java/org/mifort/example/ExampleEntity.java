package org.mifort.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RedisHash(value = ExampleEntity.KEYSPACE, timeToLive = ExampleEntity.EXPIRATION_TTL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExampleEntity {

    static final String KEYSPACE = "example";
    
    // expiration in seconds
    static final long EXPIRATION_TTL = 10L;
    
    @Id private String id;
    @Indexed private String testField1;
    @Indexed private String testField2;
    
    private Date testDate;
}
