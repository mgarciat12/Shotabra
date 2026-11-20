package com.shotabra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa los {@code @Scheduled}; los tests lo desactivan con {@code app.scheduling.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
