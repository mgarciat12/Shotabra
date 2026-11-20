package com.shotabra.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia de la aplicación (prefijo {@code app}).
 *
 * @param publicUrl             URL pública para el QR; vacía = se deduce de la petición
 * @param allowedOrigins        patrones de origen permitidos para el endpoint STOMP
 * @param hostDisconnectTimeout tiempo desconectado tras el que el host cede su rol
 * @param inactivePartyTtl      inactividad tras la que se borra una party
 * @param minPlayers            jugadores mínimos para iniciar
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicUrl,
        List<String> allowedOrigins,
        Duration hostDisconnectTimeout,
        Duration inactivePartyTtl,
        int minPlayers) {

    public AppProperties {
        publicUrl = publicUrl == null ? "" : publicUrl.strip().replaceAll("/+$", "");
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty() ? List.of("*") : List.copyOf(allowedOrigins);
        hostDisconnectTimeout = hostDisconnectTimeout == null ? Duration.ofSeconds(60) : hostDisconnectTimeout;
        inactivePartyTtl = inactivePartyTtl == null ? Duration.ofHours(6) : inactivePartyTtl;
        minPlayers = minPlayers <= 0 ? 3 : minPlayers;
    }
}
