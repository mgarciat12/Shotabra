package com.shotabra.realtime;

import java.util.Map;
import java.util.UUID;

import com.shotabra.game.event.GameEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduce los {@link GameEvent} a mensajes WebSocket, solo tras el commit de la transacción.
 * Regla clave: las asignaciones únicamente van a la cola privada de su dueño.
 */
@Component
public class GameNotifier {

    public static final String PRIVATE_DESTINATION = "/queue/events";

    private final SimpMessagingTemplate ws;

    public GameNotifier(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvent event) {
        String code = event.partyCode();
        switch (event) {
            case GameEvent.PlayerJoined e -> broadcast(code, "PLAYER_JOINED", e.player());
            case GameEvent.PlayerLeft e -> {
                broadcast(code, "PLAYER_LEFT", Map.of("playerId", e.playerId(), "name", e.name(), "kicked", e.kicked()));
                if (e.kicked()) {
                    toPlayer(e.token(), "KICKED", Map.of());
                }
            }
            case GameEvent.PresenceChanged e ->
                    broadcast(code, "PRESENCE_CHANGED", Map.of("playerId", e.playerId(), "connected", e.connected()));
            case GameEvent.HostChanged e -> broadcast(code, "HOST_CHANGED", Map.of("hostId", e.hostId()));
            case GameEvent.RoundStarted e -> {
                broadcast(code, "GAME_STARTED", Map.of("round", e.round()));
                e.assignments().forEach((token, card) -> toPlayer(token, "ASSIGNMENT", card));
            }
            case GameEvent.AccusationCreated e -> toPlayer(e.accusedToken(), "ACCUSATION_RECEIVED", e.accusation());
            case GameEvent.AccusationRejected e ->
                    toPlayer(e.accuserToken(), "ACCUSATION_REJECTED", Map.of("accusationId", e.accusationId(), "by", e.by()));
            case GameEvent.RoundOver e -> broadcast(code, "ROUND_OVER", e.result());
            case GameEvent.BackToLobby e -> broadcast(code, "BACK_TO_LOBBY", Map.of());
        }
    }

    private void broadcast(String partyCode, String type, Object data) {
        ws.convertAndSend(PlayerChannelInterceptor.PARTY_TOPIC_PREFIX + partyCode, new WsMessage(type, data));
    }

    private void toPlayer(UUID token, String type, Object data) {
        ws.convertAndSendToUser(token.toString(), PRIVATE_DESTINATION, new WsMessage(type, data));
    }
}
