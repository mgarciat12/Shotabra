package com.shotabra;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Base para tests de integración: PostgreSQL de Testcontainers, reloj controlable, sin jobs
 * programados, servidor real en puerto aleatorio (para WebSocket) y MockMvc.
 * Todas las subclases comparten el mismo contexto de Spring (y el mismo contenedor).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.scheduling.enabled=false")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IntegrationTest.ClockConfig.class})
public abstract class IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    public static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-06-01T20:00:00Z"));
        }
    }
}
