package com.shotabra.game.view;

/** Acusación que han hecho contra mí y tengo que confirmar o rechazar. */
public record IncomingAccusationView(long id, PlayerRef accuser, String word) {
}
