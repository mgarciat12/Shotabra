package com.shotabra.game.view;

import java.time.Instant;

/** Acusación que he hecho yo y espera respuesta. */
public record OutgoingAccusationView(long id, PlayerRef target, String word, Instant createdAt) {
}
