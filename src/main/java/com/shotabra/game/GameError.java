package com.shotabra.game;

import org.springframework.http.HttpStatus;

/** Errores de dominio con su código HTTP y un mensaje listo para mostrar al jugador. */
public enum GameError {
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "No existe ninguna party con ese código."),
    PLAYER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "No estás en ninguna party."),
    NOT_HOST(HttpStatus.FORBIDDEN, "Solo el host puede hacer eso."),
    INVALID_STATE(HttpStatus.CONFLICT, "Ahora mismo no se puede hacer eso."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, "El nombre debe tener entre 1 y 16 caracteres."),
    NAME_TAKEN(HttpStatus.CONFLICT, "Ese nombre ya está cogido en esta party."),
    INVALID_EMOJI(HttpStatus.BAD_REQUEST, "Elige un emoji de la lista."),
    EMOJI_TAKEN(HttpStatus.CONFLICT, "Ese emoji ya lo tiene otro jugador."),
    PARTY_FULL(HttpStatus.CONFLICT, "La party está llena."),
    GAME_IN_PROGRESS(HttpStatus.CONFLICT, "La partida ya ha empezado. Pide al host que vuelva al lobby."),
    NOT_ENOUGH_PLAYERS(HttpStatus.CONFLICT, "Hacen falta al menos 3 jugadores."),
    ACCUSATION_ALREADY_PENDING(HttpStatus.CONFLICT, "Ya tienes una acusación pendiente."),
    ACCUSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Esa acusación ya no existe."),
    NOT_YOUR_ACCUSATION(HttpStatus.FORBIDDEN, "Esa acusación no va contigo."),
    CANNOT_KICK_SELF(HttpStatus.BAD_REQUEST, "No puedes expulsarte a ti mismo."),
    NOT_ENOUGH_WORDS(HttpStatus.INTERNAL_SERVER_ERROR, "No hay suficientes palabras activas.");

    private final HttpStatus status;
    private final String message;

    GameError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
