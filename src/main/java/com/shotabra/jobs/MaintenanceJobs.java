package com.shotabra.jobs;

import com.shotabra.game.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas periódicas: traspaso de host y limpieza de parties inactivas. */
@Component
public class MaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final GameService game;

    public MaintenanceJobs(GameService game) {
        this.game = game;
    }

    /** Tras un reinicio del servidor no queda ninguna sesión WebSocket: todos desconectados. */
    @EventListener(ApplicationReadyEvent.class)
    public void resetPresenceOnStartup() {
        int n = game.resetPresence();
        if (n > 0) {
            log.info("Presencia reiniciada para {} jugadores", n);
        }
    }

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT10S")
    public void transferStaleHosts() {
        game.transferStaleHosts();
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void cleanupInactiveParties() {
        game.cleanupInactiveParties();
    }
}
