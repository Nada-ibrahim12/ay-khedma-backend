package com.aykhedma;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class TestAyKhedmaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(AyKhedmaBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
