package com.shotabra.game;

import java.text.Normalizer;

/** Validación y normalización del nombre del jugador. */
public final class PlayerNames {

    public static final int MAX_LENGTH = 16;

    private PlayerNames() {
    }

    /** @return el nombre limpio, o lanza {@link GameError#INVALID_NAME}. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new GameException(GameError.INVALID_NAME);
        }
        String name = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .strip();
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > MAX_LENGTH || name.codePoints().anyMatch(Character::isISOControl)
                || name.chars().anyMatch(c -> c == '<' || c == '>')) {
            throw new GameException(GameError.INVALID_NAME);
        }
        return name;
    }
}
