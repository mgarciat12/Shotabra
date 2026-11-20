package com.shotabra.game;

public class GameException extends RuntimeException {

    private final GameError error;

    public GameException(GameError error) {
        super(error.message());
        this.error = error;
    }

    public GameError error() {
        return error;
    }
}
