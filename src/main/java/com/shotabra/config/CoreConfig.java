package com.shotabra.config;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Aleatoriedad del juego (asignaciones, palabras, códigos). */
    @Bean
    RandomGenerator gameRandom() {
        return new SecureRandom();
    }
}
