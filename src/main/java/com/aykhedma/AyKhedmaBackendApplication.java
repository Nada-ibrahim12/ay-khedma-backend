package com.aykhedma;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootApplication
@PropertySource("file:app.env")
@EnableCaching
public class AyKhedmaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AyKhedmaBackendApplication.class, args);
    }

//    @Bean
//    CommandLineRunner testRedis(RedisTemplate<String, String> redisTemplate) {
//        return args -> {
//            redisTemplate.opsForValue().set("test-key", "hello");
//            System.out.println("Redis test: " + redisTemplate.opsForValue().get("test-key"));
//        };
//    }

}