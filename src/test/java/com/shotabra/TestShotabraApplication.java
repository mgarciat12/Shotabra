package com.shotabra;

import org.springframework.boot.SpringApplication;

/** Arranca la app con un PostgreSQL de Testcontainers (útil para probar en local sin compose). */
public class TestShotabraApplication {

    public static void main(String[] args) {
        SpringApplication.from(ShotabraApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
