package com.shotabra.game.event;

import java.util.Map;
import java.util.UUID;

import com.shotabra.game.view.AssignmentView;
import com.shotabra.game.view.IncomingAccusationView;
import com.shotabra.game.view.PlayerRef;
import com.shotabra.game.view.PlayerView;
import com.shotabra.game.view.RoundResultView;

/**
 * Eventos de dominio que publica {@code GameService}. Se envían a los clientes por WebSocket
 * solo cuando la transacción hace commit (ver {@code GameNotifier}).
 */
public sealed interface GameEvent {

    String partyCode();

    record PlayerJoined(String partyCode, PlayerView player) implements GameEvent {
    }

    /** {@code token} solo se usa para avisar en privado al expulsado; nunca se publica. */
    record PlayerLeft(String partyCode, long playerId, String name, boolean kicked, UUID token) implements GameEvent {
    }

    record PresenceChanged(String partyCode, long playerId, boolean connected) implements GameEvent {
    }

    record HostChanged(String partyCode, long hostId) implements GameEvent {
    }

    /** {@code assignments} es privado: cada entrada se envía únicamente a su jugador. */
    record RoundStarted(String partyCode, int round, Map<UUID, AssignmentView> assignments) implements GameEvent {
    }

    record AccusationCreated(String partyCode, UUID accusedToken, IncomingAccusationView accusation) implements GameEvent {
    }

    record AccusationRejected(String partyCode, UUID accuserToken, long accusationId, PlayerRef by) implements GameEvent {
    }

    record RoundOver(String partyCode, RoundResultView result) implements GameEvent {
    }

    record BackToLobby(String partyCode) implements GameEvent {
    }
}
