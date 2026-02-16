package com.aykhedma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("file:.env")
public class AyKhedmaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AyKhedmaBackendApplication.class, args);
    }

}