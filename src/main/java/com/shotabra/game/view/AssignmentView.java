package com.shotabra.game.view;

/** La tarjeta privada de un jugador: "haz que {@code target} diga {@code word}". */
public record AssignmentView(int round, PlayerRef target, String word) {
}
