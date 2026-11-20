package com.shotabra.realtime;

import java.security.Principal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.shotabra.game.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Cuenta las sesiones STOMP abiertas por jugador (puede tener varias pestañas o una reconexión
 * que se solapa con la anterior). Solo cambia el estado en BD al pasar de 0 a 1 o de 1 a 0.
 */
@Component
public class PresenceTracker {

    private static final Logger log = LoggerFactory.getLogger(PresenceTracker.class);

    private final Map<UUID, Set<String>> sessions = new ConcurrentHashMap<>();
    private final GameService game;

    public PresenceTracker(GameService game) {
        this.game = game;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        UUID token = token(event);
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (token == null || sessionId == null) {
            return;
        }
        // compute() serializa por jugador, así la BD ve los cambios en el mismo orden que ocurren.
        sessions.compute(token, (t, set) -> {
            Set<String> s = set == null ? new HashSet<>() : set;
            if (s.isEmpty()) {
                safely(() -> game.playerConnected(t));
            }
            s.add(sessionId);
            return s;
        });
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        UUID token = token(event);
        if (token == null) {
            return;
        }
        sessions.computeIfPresent(token, (t, s) -> {
            s.remove(event.getSessionId());
            if (s.isEmpty()) {
                safely(() -> game.playerDisconnected(t));
                return null;
            }
            return s;
        });
    }

    public boolean isOnline(UUID token) {
        return sessions.containsKey(token);
    }

    private static UUID token(AbstractSubProtocolEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return null;
        }
        try {
            return UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.debug("No se pudo actualizar la presencia: {}", e.getMessage());
        }
    }
}
